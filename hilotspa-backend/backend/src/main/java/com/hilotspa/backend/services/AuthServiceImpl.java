package com.hilotspa.backend.services;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.hilotspa.backend.entities.Role;
import com.hilotspa.backend.entities.User;
import com.hilotspa.backend.model.AuthResponse;
import com.hilotspa.backend.model.LoginRequest;
import com.hilotspa.backend.model.RegisterRequest;
import com.hilotspa.backend.repository.UserRepository;

@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Value("${hilotspa.jwt.expiration-ms}")
    private long expirationMs;

    @Value("${hilotspa.node.id}")
    private String nodeId;

    @Override
    public AuthResponse register(RegisterRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }
        if (request.getPassword() == null || request.getPassword().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be at least 8 characters");
        }

        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.findUserByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That email is already registered");
        }

        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setMiddleName(request.getMiddleName());
        user.setContact(request.getContact());
        user.setAddress(request.getAddress());
        user.setEmail(email);

        // Hashed immediately. The plaintext is never stored, logged, or returned.
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        // Self-registration always produces a CUSTOMER. Never trust a role from the client.
        user.setRole(Role.CUSTOMER);
        user.setEnabled(true);
        user.setBranch(null);

        return issueToken(userRepository.save(user));
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();

        User user = userRepository.findUserByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        // Same message for "no such user" and "wrong password" on purpose — telling them
        // apart lets an attacker enumerate which emails are registered.
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account has been deactivated");
        }

        return issueToken(user);
    }

    private AuthResponse issueToken(User user) {
        Instant now = Instant.now();
        long expiresInSeconds = expirationMs / 1000;

        String fullName = (user.getFirstName() + " " + user.getLastName()).trim();

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("hilotspa")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expiresInSeconds))
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("name", fullName)
                .claim("nodeId", nodeId)
                // JwtGrantedAuthoritiesConverter reads this and prefixes ROLE_
                .claim("roles", List.of(user.getRole().name()));

        // Only staff carry a branch. This claim is what Process Rule #5 scopes queries by.
        if (user.getBranch() != null) {
            claims.claim("branchId", user.getBranch().getId().toString());
        }

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();

        AuthResponse response = new AuthResponse();
        response.setToken(token);
        response.setExpiresInSeconds(expiresInSeconds);
        response.setUserId(user.getId());
        response.setEmail(user.getEmail());
        response.setFullName(fullName);
        response.setRole(user.getRole());
        response.setBranchId(user.getBranch() == null ? null : user.getBranch().getId());
        return response;
    }
}
