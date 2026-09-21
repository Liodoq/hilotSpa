package com.hilotspa.backend.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hilotspa.backend.config.CurrentUser;
import com.hilotspa.backend.entities.AuditLog;
import com.hilotspa.backend.entities.PasswordResetToken;
import com.hilotspa.backend.entities.User;
import com.hilotspa.backend.model.PasswordDtos.AdminResetRequest;
import com.hilotspa.backend.model.PasswordDtos.AdminResetResult;
import com.hilotspa.backend.model.PasswordDtos.ForgotRequest;
import com.hilotspa.backend.model.PasswordDtos.ForgotResult;
import com.hilotspa.backend.model.PasswordDtos.ResetRequest;
import com.hilotspa.backend.repository.AuditLogRepository;
import com.hilotspa.backend.repository.PasswordResetTokenRepository;
import com.hilotspa.backend.repository.UserRepository;

/**
 * Forgotten passwords.
 *
 * THE ONE THING THIS CLASS IS BUILT AROUND: /auth/forgot-password is open to
 * the internet and takes an email address. Anything it does differently for an
 * address that HAS an account is a way for a stranger to find out who comes to
 * this spa. So the public path returns one fixed sentence, at roughly one fixed
 * cost, whatever it finds - the unknown-address case walks the same code and
 * simply stops before sending. This mirrors the decision already made for
 * login, where an unknown email and a wrong password return the identical 401.
 *
 * The second-order leak is timing and volume: if a real address triggers an
 * email and an unknown one does not, somebody watching an inbox or a rate limit
 * can still tell. Nothing here can close that completely without a mail queue,
 * and saying so is more honest than pretending otherwise. It is recorded in the
 * paper deltas rather than hidden.
 */
@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final Logger LOG = LoggerFactory.getLogger(PasswordResetServiceImpl.class);

    /**
     * The sentence every caller gets. Deliberately does not say "we have sent"
     * - we may not have, and a promise the system cannot keep is worse than a
     * careful one it can.
     */
    private static final String SAME_ANSWER =
            "If that email address has an account with us, a reset link is on its way. "
            + "It expires in %d minutes. Check your spam folder if it does not arrive.";

    /** No 0/O/1/l/I. A temporary password gets read aloud across a counter. */
    private static final String SAY_ABLE = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private static final int MIN_PASSWORD = 8;

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordResetTokenRepository tokenRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    /**
     * ObjectProvider, not a plain @Autowired: Spring only creates a
     * JavaMailSender when spring.mail.host is set, and a node with no mail
     * configured must still boot and still serve bookings. Same pattern as
     * ReminderServiceImpl.
     */
    @Autowired private ObjectProvider<JavaMailSender> mailSenders;

    @Value("${hilotspa.reset.ttl-minutes:30}")          private int ttlMinutes;
    @Value("${hilotspa.reset.min-interval-seconds:60}") private int minIntervalSeconds;
    @Value("${hilotspa.reset.link-base:}")              private String linkBase;
    @Value("${hilotspa.cors.allowed-origin}")           private String allowedOrigin;
    @Value("${spring.mail.host:}")                      private String mailHost;
    @Value("${hilotspa.reminders.from:}")               private String mailFrom;
    @Value("${hilotspa.reminders.reply-to:}")           private String replyTo;
    @Value("${hilotspa.node.id:local-dev}")             private String nodeId;
    @Value("${hilotspa.spa.name:Knead Wellness Spa}")   private String spaName;

    private final SecureRandom rng = new SecureRandom();

    // --------------------------------------------------------------- public

    /**
     * Deliberately NOT @Transactional.
     *
     * This method talks to an SMTP server, and Gmail's handshake is allowed ten
     * seconds each way by spring.mail.properties. Inside a transaction that
     * would hold a pooled database connection for the whole exchange - on the
     * ONE endpoint on this server that anybody on the internet can call as
     * often as they like. Ten connections in the pool and ten slow sends is an
     * outage, caused by a form, with no attacker needing anything clever. Same
     * reasoning as PeerDirectory, which is not transactional for the same
     * reason: never hold a connection across a call to a machine you do not
     * control.
     *
     * Nothing is lost by dropping it. The only write here is one token row, and
     * a single save() is already atomic on its own.
     */
    @Override
    public ForgotResult forgot(ForgotRequest body) {
        ForgotResult answer = new ForgotResult(String.format(SAME_ANSWER, ttlMinutes));

        String email = body == null || body.email() == null ? "" : body.email().trim().toLowerCase();
        if (email.isEmpty()) {
            return answer;
        }

        User user = userRepository.findUserByEmail(email).orElse(null);
        if (user == null) {
            // Logged at DEBUG, not INFO. An operator scrolling the log should
            // not be shown a list of addresses that tried and do not exist.
            LOG.debug("Reset asked for an address with no account");
            return answer;
        }
        if (!user.isEnabled()) {
            // A disabled account is disabled on purpose. Letting it be reset
            // would be a way back in for exactly the person who was shut out.
            LOG.info("Reset asked for disabled account {}", user.getId());
            return answer;
        }
        if (askedTooRecently(user.getId())) {
            LOG.info("Reset for {} throttled - one was issued less than {}s ago",
                    user.getId(), minIntervalSeconds);
            return answer;
        }

        issueAndSend(user, "SELF");
        return answer;
    }

    @Override
    @Transactional
    public void reset(ResetRequest body) {
        if (body == null || body.token() == null || body.token().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "That link is missing its token. Open the link from the email again.");
        }
        String newPassword = body.newPassword();
        if (newPassword == null || newPassword.length() < MIN_PASSWORD) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The new password must be at least " + MIN_PASSWORD + " characters.");
        }

        PasswordResetToken token = tokenRepository.findByTokenHash(sha256(body.token().trim()))
                .orElse(null);
        LocalDateTime now = LocalDateTime.now();

        // One message for every way a token can fail - not found, already
        // spent, expired. Telling them WHICH would confirm that a token once
        // existed, and none of the three is actionable differently: ask again.
        if (token == null || !token.isRedeemable(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "That reset link is no longer valid. Links last " + ttlMinutes
                    + " minutes and can be used once. Ask for a new one.");
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        token.setUsedAt(now);
        tokenRepository.save(token);
        expireOthers(user.getId(), token.getId(), now);

        audit(user, user, "PASSWORD_RESET_COMPLETED",
                "Password reset using a link sent to " + user.getEmail()
                + " (issued by " + token.getIssuedBy() + ")");
        LOG.info("Password reset completed for {}", user.getId());
    }

    // ---------------------------------------------------------------- admin

    /**
     * This one KEEPS @Transactional, and the difference from forgot() is worth
     * stating. TEMPORARY writes three things - the new hash, every outstanding
     * token spent, the audit line - and a half-done version of that leaves a
     * live reset link for a password somebody has just changed. That needs to
     * be one unit. EMAIL does send mail inside the transaction, which is the
     * thing forgot() avoids; it is tolerable only because this endpoint is
     * ADMIN-only and one person at a counter cannot flood it.
     */
    @Override
    @Transactional
    public AdminResetResult adminReset(UUID userId, AdminResetRequest body) {
        User user = userRepository.findById(userId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "That account does not exist."));

        String mode = body == null || body.mode() == null ? "" : body.mode().trim().toUpperCase();
        User actor = CurrentUser.id().flatMap(userRepository::findById).orElse(null);

        if ("EMAIL".equals(mode)) {
            if (user.getEmail() == null || user.getEmail().isBlank()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "That account has no email address, so there is nowhere to send a link. "
                        + "Set a temporary password instead.");
            }
            if (!mailAvailable()) {
                // Do NOT report success here. An administrator who believes a
                // link went out will tell the client to check their email, and
                // the client will wait for something that was never sent.
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This node has no mail server configured, so no link can be sent. "
                        + "Set a temporary password instead.");
            }
            issueAndSend(user, "ADMIN");
            audit(user, actor, "PASSWORD_RESET_SENT_BY_ADMIN",
                    "Reset link sent to " + user.getEmail());
            return new AdminResetResult("EMAIL", null,
                    "A reset link is on its way to " + user.getEmail()
                    + ". It expires in " + ttlMinutes + " minutes.");
        }

        if (!"TEMPORARY".equals(mode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Mode must be TEMPORARY or EMAIL.");
        }

        String supplied = body.temporaryPassword() == null ? "" : body.temporaryPassword().trim();
        if (!supplied.isEmpty() && supplied.length() < MIN_PASSWORD) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A temporary password must be at least " + MIN_PASSWORD + " characters.");
        }
        String temporary = supplied.isEmpty() ? generated() : supplied;

        user.setPasswordHash(passwordEncoder.encode(temporary));
        userRepository.save(user);

        // Any link already in flight is now a way to change a password that was
        // just changed by somebody else. Kill them.
        expireOthers(user.getId(), null, LocalDateTime.now());

        audit(user, actor, "PASSWORD_RESET_BY_ADMIN",
                "Temporary password set at the counter for " + user.getEmail()
                + ". The password itself is not recorded.");
        LOG.info("Temporary password set for {} by {}", user.getId(),
                actor == null ? "unknown" : actor.getId());

        return new AdminResetResult("TEMPORARY", temporary,
                "Read this to them now. It is shown once and is not stored anywhere in "
                + "readable form - if it is lost, set another one.");
    }

    // ------------------------------------------------------------- internals

    /** Mint a token, store only its hash, and put the plaintext in an email. */
    private void issueAndSend(User user, String issuedBy) {
        byte[] raw = new byte[32];
        rng.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        PasswordResetToken row = new PasswordResetToken();
        row.setUser(user);
        row.setTokenHash(sha256(token));
        row.setExpiresAt(LocalDateTime.now().plusMinutes(ttlMinutes));
        row.setIssuedBy(issuedBy);
        row.setOriginNodeId(nodeId);
        tokenRepository.save(row);

        send(user, token);
    }

    private void send(User user, String token) {
        if (!mailAvailable()) {
            LOG.warn("No mail sender on this node - reset link for {} was issued but not sent",
                    user.getId());
            return;
        }
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setTo(user.getEmail());
            if (mailFrom != null && !mailFrom.isBlank())   { mail.setFrom(mailFrom); }
            if (replyTo != null && !replyTo.isBlank())     { mail.setReplyTo(replyTo); }
            mail.setSubject("Reset your " + spaName + " password");
            mail.setText(bodyOf(user, token));
            mailSenders.getIfAvailable().send(mail);
            LOG.info("Reset link sent for {}", user.getId());
        } catch (Exception e) {
            // Swallowed on purpose for the PUBLIC path: a caller who learns
            // that sending failed has learned the address exists. The operator
            // gets the truth in the log; the browser gets the same sentence as
            // everybody else.
            LOG.warn("Reset mail for {} failed - {}", user.getId(), e.toString());
        }
    }

    /**
     * Plain text, like the reminder. The link has to survive being read on a
     * cheap phone with images off.
     */
    private String bodyOf(User user, String token) {
        String name = user.getFirstName() == null || user.getFirstName().isBlank()
                ? "there" : user.getFirstName();
        return "Hello " + name + ",\n\n"
             + "Somebody asked to reset the password for your " + spaName + " account.\n\n"
             + "Open this link to choose a new one:\n\n"
             + resetLink(token) + "\n\n"
             + "The link works once and expires in " + ttlMinutes + " minutes.\n\n"
             + "If this was not you, you can ignore this email. Your password has not "
             + "changed and nobody can use this link without opening it.\n\n"
             + "- " + spaName + "\n";
    }

    /**
     * Where the link points.
     *
     * It must be THIS node's own site. The token lives in this node's database
     * and nowhere else, so a link to the other branch's hostname would open a
     * page that cannot redeem it. hilotspa.reset.link-base overrides; otherwise
     * the first configured CORS origin is used, which is by definition the site
     * this API answers for.
     */
    private String resetLink(String token) {
        String base = linkBase == null ? "" : linkBase.trim();
        if (base.isEmpty()) {
            base = java.util.Arrays.stream(allowedOrigin.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .findFirst()
                    .orElse("");
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/reset-password?token=" + token;
    }

    /** True when a token was issued for this account within the throttle window. */
    private boolean askedTooRecently(UUID userId) {
        LocalDateTime floor = LocalDateTime.now().minusSeconds(minIntervalSeconds);
        return tokenRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .findFirst()
                .map(t -> t.getCreatedAt() != null && t.getCreatedAt().isAfter(floor))
                .orElse(false);
    }

    /** Spend every other live token for this account. */
    private void expireOthers(UUID userId, UUID keepId, LocalDateTime now) {
        List<PasswordResetToken> live = tokenRepository.findByUserIdOrderByCreatedAtDesc(userId);
        for (PasswordResetToken t : live) {
            if (keepId != null && keepId.equals(t.getId())) { continue; }
            if (t.getUsedAt() != null) { continue; }
            t.setUsedAt(now);
            tokenRepository.save(t);
        }
    }

    private boolean mailAvailable() {
        return mailHost != null && !mailHost.isBlank() && mailSenders.getIfAvailable() != null;
    }

    private String generated() {
        StringBuilder out = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            out.append(SAY_ABLE.charAt(rng.nextInt(SAY_ABLE.length())));
        }
        return out.toString();
    }

    private static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(out.length * 2);
            for (byte b : out) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 is mandatory in every JRE. If it is missing, something is
            // wrong that a fallback would only hide.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * An audit row, never at the cost of the operation.
     *
     * A password reset that worked must not be reported as failed because the
     * audit insert did - the person's password HAS changed by then, and telling
     * them otherwise is the worse lie. Same shape as ProtocolServiceImpl.
     */
    private void audit(User subject, User actor, String action, String details) {
        try {
            AuditLog row = new AuditLog();
            row.setAction(action);
            row.setEntityType("User");
            row.setEntityId(subject.getId());
            row.setOriginNodeId(nodeId);
            row.setBranch(subject.getBranch());
            row.setActor(actor);
            row.setDetails(details);
            auditLogRepository.save(row);
        } catch (RuntimeException e) {
            LOG.warn("audit write failed for {} {} - {}", action, subject.getId(), e.toString());
        }
    }
}
