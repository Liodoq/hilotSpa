package com.hilotspa.backend.services;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hilotspa.backend.config.CurrentUser;
import com.hilotspa.backend.entities.Appointment;
import com.hilotspa.backend.entities.AppointmentStatus;
import com.hilotspa.backend.entities.AssessmentIntent;
import com.hilotspa.backend.entities.AuditLog;
import com.hilotspa.backend.entities.Branch;
import com.hilotspa.backend.entities.Forms;
import com.hilotspa.backend.entities.PatientIntake;
import com.hilotspa.backend.entities.Role;
import com.hilotspa.backend.entities.User;
import com.hilotspa.backend.model.FormsModel;
import com.hilotspa.backend.model.FormsModel.Visit;
import com.hilotspa.backend.model.PatientIntakeModel;
import com.hilotspa.backend.repository.AppointmentRepository;
import com.hilotspa.backend.repository.AuditLogRepository;
import com.hilotspa.backend.repository.BranchRepository;
import com.hilotspa.backend.repository.FormsRepository;
import com.hilotspa.backend.repository.UserRepository;
import com.hilotspa.backend.transformer.FormsTransform;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class FormsServiceImpl implements FormsService {

    /**
     * How stale an assessment may be and still be reusable.
     *
     * Not a clinical number - nobody has signed one. It is deliberately short so
     * that the failure mode is an extra three minutes of form filling rather
     * than a therapist working from a body map drawn last season. Raise it only
     * with the practitioner's agreement.
     */
    private static final int REUSE_MAX_DAYS = 60;

    @Autowired
    private FormsRepository formsRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FormsTransform formsTransform;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    /** Same wording as the booking screens, so one visit never reads two ways. */
    private static final DateTimeFormatter LABEL =
            DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.ENGLISH);

    @Value("${hilotspa.node.id:local-dev}")
    private String nodeId;

    @Override
    public FormsModel createForm(FormsModel model) {
        UUID actorId = CurrentUser.id()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Not authenticated"));

        //Form's owner
        UUID ownerId;
        UUID branchId;

        if (CurrentUser.isAdmin()) {
            // Administrator may record on behalf of anyone, at any branch.
            ownerId = model.getUserId();
            branchId = model.getBranchId();

        } else if (CurrentUser.hasRole(Role.STAFF)) {
            // Staff record intake for walk-in clients, but only at their own branch.
            ownerId = model.getUserId();
            branchId = CurrentUser.branchId()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.FORBIDDEN, "Staff account has no branch assigned"));

        } else {
            // Customer: the form is theirs, whatever the body said. A client can
            // never record an assessment in someone else's name.
            ownerId = actorId;
            branchId = model.getBranchId();
            model.setWalkInName(null);
        }

        // B85 - a walk-in has no account, so staff name them instead. Exactly
        // one of the two identifies the record, and the database enforces the
        // same rule through the forms_has_a_client check constraint.
        String walkInName = model.getWalkInName() == null ? null : model.getWalkInName().trim();
        boolean isWalkIn = ownerId == null && walkInName != null && !walkInName.isBlank();

        if (branchId == null || (ownerId == null && !isWalkIn)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "branchId is required, and either userId or a walk-in name");
        }
        if (ownerId != null && walkInName != null && !walkInName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "An assessment belongs to an account or to a named walk-in, not both");
        }

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Branch not found"));
        User user = ownerId == null ? null : userRepository.findById(ownerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "User not found"));

        requireComplaintWhenInPain(model);

        Forms form = new Forms();
        applyFields(form, model);
        form.setBranch(branch);
        form.setUser(user);
        form.setWalkInName(isWalkIn ? walkInName : null);
        replacePainPoints(form, model.getPainPoints());

        return formsTransform.transform(formsRepository.save(form));
    }

    @Override
    public FormsModel getFormById(UUID id) {
        Forms form = formsRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Form not found"));
        assertCanAccess(form);
        FormsModel model = formsTransform.transform(form);
        model.setVisit(latestVisit(appointmentRepository.findByFormId(form.getId())));
        return model;
    }

    @Override
    public List<FormsModel> getAllForms() {
        List<Forms> forms;

        if (CurrentUser.isAdmin()) {
            forms = formsRepository.findAll();

        } else if (CurrentUser.hasRole(Role.STAFF)) {
            UUID branchId = CurrentUser.branchId()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.FORBIDDEN, "Staff account has no branch assigned"));
            forms = formsRepository.findByBranchId(branchId);

        } else {
            UUID userId = CurrentUser.id()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.UNAUTHORIZED, "Not authenticated"));
            forms = formsRepository.findByUserId(userId);
        }

        List<FormsModel> models = forms.stream()
                .map(formsTransform::transform)
                .collect(Collectors.toList());

        // One query for the whole page. Asking per row turns a history list into
        // N round trips, and this endpoint is what the client's home screen and
        // session list both read.
        List<UUID> ids = forms.stream().map(Forms::getId).filter(java.util.Objects::nonNull).toList();
        if (!ids.isEmpty()) {
            Map<UUID, List<Appointment>> byForm = new HashMap<>();
            for (Appointment a : appointmentRepository.findByFormIdIn(ids)) {
                if (a.getForm() != null) {
                    byForm.computeIfAbsent(a.getForm().getId(), k -> new java.util.ArrayList<>()).add(a);
                }
            }
            for (FormsModel m : models) {
                m.setVisit(latestVisit(byForm.get(m.getId())));
            }
        }
        return models;
    }

    @Override
    public FormsModel updateForm(UUID id, FormsModel model) {
        Forms form = formsRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Form not found"));
        assertCanAccess(form);

        requireComplaintWhenInPain(model);

        // applyFields deliberately does not touch user or branch — ownership is
        // set once, at creation, and cannot be reassigned by an update.
        applyFields(form, model);
        replacePainPoints(form, model.getPainPoints());

        return formsTransform.transform(formsRepository.save(form));
    }

    @Override
    @Transactional
    public FormsModel reuse(UUID id) {
        Forms source = formsRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Form not found"));
        assertCanAccess(source);

        // Old enough that "nothing has changed" stops being a safe assumption.
        // The client is not refused - they are sent through the short form again.
        if (source.getCreatedAt() != null
                && source.getCreatedAt().isBefore(LocalDateTime.now().minusDays(REUSE_MAX_DAYS))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "That assessment is more than " + REUSE_MAX_DAYS
                    + " days old. Please answer the short form again.");
        }

        Forms copy = new Forms();
        copy.setUser(source.getUser());
        // Carry the walk-in name across too. Copying the user alone would leave a
        // reused walk-in assessment identifying nobody, and the forms_has_a_client
        // constraint would reject it at INSERT with nothing useful to say. B85.
        copy.setWalkInName(source.getWalkInName());
        // Safety flags are the whole reason a reuse is safe to offer at all -
        // dropping them would hand the assistant a client with no contraindications.
        copy.getSafetyFlags().addAll(source.getSafetyFlags());
        copy.setPressurePreference(source.getPressurePreference());
        copy.setTherapistPreference(source.getTherapistPreference());
        copy.setBranch(source.getBranch());
        copy.setIntent(source.getIntent());
        copy.setMainComplaint(source.getMainComplaint());
        copy.setMainComplaintOther(source.getMainComplaintOther());
        copy.setMainComplaintDuration(source.getMainComplaintDuration());
        copy.setHadIllness(source.getHadIllness());
        copy.setMedicalHistory(source.getMedicalHistory());
        copy.setHasTherapy(source.getHasTherapy());
        copy.setTherapyDetail(source.getTherapyDetail());
        copy.setStatus(source.getStatus());

        String note = "Reused from the assessment of "
                + (source.getCreatedAt() == null ? "an earlier visit"
                        : source.getCreatedAt().toLocalDate())
                + "; the client confirmed nothing had changed.";
        copy.setRemarks(source.getRemarks() == null || source.getRemarks().isBlank()
                ? note : source.getRemarks() + " | " + note);

        for (PatientIntake p : source.getPainPoints()) {
            PatientIntake point = new PatientIntake();
            point.setForm(copy);
            point.setBodyView(p.getBodyView());
            point.setAnatomicalRegion(p.getAnatomicalRegion());
            point.setCoordinateX(p.getCoordinateX());
            point.setCoordinateY(p.getCoordinateY());
            point.setPainScoreBefore(p.getPainScoreBefore());
            point.setSide(p.getSide());
            point.setComplaintType(p.getComplaintType());
            // painScoreAfter is NOT copied - it was the outcome of that session.
            copy.getPainPoints().add(point);
        }

        Forms saved = formsRepository.save(copy);
        audit(saved, source);
        return formsTransform.transform(saved);
    }

    /** A reused assessment must be distinguishable from a fresh one, always. */
    private void audit(Forms copy, Forms source) {
        try {
            AuditLog row = new AuditLog();
            row.setAction("ASSESSMENT_REUSED");
            row.setEntityType("Forms");
            row.setEntityId(copy.getId());
            row.setBranch(copy.getBranch());
            row.setActor(copy.getUser());
            row.setOriginNodeId(nodeId);
            row.setDetails("copied from " + source.getId()
                    + " dated " + source.getCreatedAt()
                    + " / " + copy.getPainPoints().size() + " pain points carried over");
            auditLogRepository.save(row);
        } catch (RuntimeException e) {
            log.warn("audit write failed for ASSESSMENT_REUSED {} - {}", copy.getId(), e.toString());
        }
    }

    /**
     * The visit to show for an assessment: the most recent one that still counts.
     *
     * Cancelled appointments are skipped - a client who cancelled and rebooked
     * should see the booking they kept. When every appointment was cancelled the
     * latest cancelled one is shown rather than nothing, because "you cancelled
     * this" is information and a blank row is not.
     */
    private Visit latestVisit(List<Appointment> found) {
        if (found == null || found.isEmpty()) {
            return null;
        }
        Appointment best = null;
        for (Appointment a : found) {
            if (a.getStartTime() == null) {
                continue;
            }
            boolean live = a.getStatus() != AppointmentStatus.CANCELLED;
            boolean bestLive = best != null && best.getStatus() != AppointmentStatus.CANCELLED;
            if (best == null
                    || (live && !bestLive)
                    || (live == bestLive && a.getStartTime().isAfter(best.getStartTime()))) {
                best = a;
            }
        }
        if (best == null) {
            return null;
        }
        return new Visit(
                best.getId(),
                best.getService() == null ? "" : best.getService().getName(),
                best.getEndTime() == null ? 0
                        : (int) Duration.between(best.getStartTime(), best.getEndTime()).toMinutes(),
                best.getStartTime().format(LABEL),
                best.getStartTime(),
                best.getTherapist() == null ? ""
                        : best.getTherapist().getFirstName() + " " + best.getTherapist().getLastName(),
                best.getRoom() == null ? "" : best.getRoom().getName(),
                best.getBranch() == null ? "" : best.getBranch().getName(),
                best.getStatus() == null ? "" : best.getStatus().name());
    }

    /**
     * B45 - a PAIN assessment must say what hurts.
     *
     * The wizard has always enforced this in the browser, which stops an honest
     * client and nobody else. An assessment with intent=PAIN and no complaint
     * reaches the assistant with nothing to filter the service protocol against,
     * so it would recommend from the whole menu with no contraindication check
     * having anything to check. That is the one failure this system exists to
     * prevent, so the rule belongs on the server.
     */
    private void requireComplaintWhenInPain(FormsModel model) {
        if (model.getIntent() != AssessmentIntent.PAIN) {
            return;
        }
        boolean named = model.getMainComplaint() != null
                || (model.getMainComplaintOther() != null
                    && !model.getMainComplaintOther().isBlank());
        if (!named) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A pain assessment needs a main complaint, or a description under Others.");
        }
    }

    private void applyFields(Forms form, FormsModel model) {
        form.setMainComplaint(model.getMainComplaint());
        form.setMainComplaintOther(model.getMainComplaintOther());
        form.setMainComplaintDuration(model.getMainComplaintDuration());
        form.setHasTherapy(model.getHasTherapy());
        form.setHadIllness(model.getHadIllness());
        form.setMedicalHistory(model.getMedicalHistory());
        form.setTherapyDetail(model.getTherapyDetail());
        form.setStatus(model.getStatus());
        form.setIntent(model.getIntent());
        form.setRemarks(model.getRemarks());

        // H9 / B44 - the safety checklist and the pressure preference have real
        // columns now. Replace rather than merge: an unticked box must be able
        // to become unticked, and a set that only ever grows would make a
        // corrected assessment impossible.
        form.getSafetyFlags().clear();
        if (model.getSafetyFlags() != null) {
            form.getSafetyFlags().addAll(model.getSafetyFlags());
        }
        form.setPressurePreference(model.getPressurePreference());
        form.setTherapistPreference(model.getTherapistPreference());
    }

    /**
     * Replaces the form's pain points with the set supplied by the client.
     * orphanRemoval = true on Forms.painPoints deletes any row that was dropped.
     */
    private void replacePainPoints(Forms form, List<PatientIntakeModel> models) {
        form.getPainPoints().clear();
        if (models == null) {
            return;
        }
        for (PatientIntakeModel pm : models) {
            PatientIntake point = new PatientIntake();
            point.setForm(form);
            point.setBodyView(pm.getBodyView());
            point.setAnatomicalRegion(pm.getAnatomicalRegion());
            point.setCoordinateX(pm.getCoordinateX());
            point.setCoordinateY(pm.getCoordinateY());
            point.setPainScoreBefore(pm.getPainScoreBefore());
            point.setSide(pm.getSide());
            // painScoreAfter is deliberately NOT taken from the request. It is
            // written by staff at the end of a session, never by the client.
            point.setComplaintType(pm.getComplaintType());
            form.getPainPoints().add(point);
        }
    }

    private void assertCanAccess(Forms form) {
        if (CurrentUser.isAdmin()) {
            return;
        }

        if (CurrentUser.hasRole(Role.STAFF)) {
            boolean sameBranch = form.getBranch() != null
                    && CurrentUser.branchId()
                        .map(b -> b.equals(form.getBranch().getId()))
                        .orElse(false);
            if (sameBranch) {
                return;
            }
        } else {
            boolean owns = form.getUser() != null
                    && CurrentUser.id()
                        .map(u -> u.equals(form.getUser().getId()))
                        .orElse(false);
            if (owns) {
                return;
            }
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Form not found");
    }
    
}
