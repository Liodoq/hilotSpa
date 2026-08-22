package com.hilotspa.backend.services;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.hilotspa.backend.config.CurrentUser;
import com.hilotspa.backend.entities.Branch;
import com.hilotspa.backend.entities.Forms;
import com.hilotspa.backend.entities.PatientIntake;
import com.hilotspa.backend.entities.Role;
import com.hilotspa.backend.entities.User;
import com.hilotspa.backend.model.FormsModel;
import com.hilotspa.backend.model.PatientIntakeModel;
import com.hilotspa.backend.repository.BranchRepository;
import com.hilotspa.backend.repository.FormsRepository;
import com.hilotspa.backend.repository.UserRepository;
import com.hilotspa.backend.transformer.FormsTransform;

@Service
public class FormsServiceImpl implements FormsService {

    @Autowired
    private FormsRepository formsRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FormsTransform formsTransform;

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
            // Customer: the form is theirs, whatever the body said.
            ownerId = actorId;
            branchId = model.getBranchId();
        }

        if (ownerId == null || branchId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "userId and branchId are required");
        }

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Branch not found"));
        User user = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "User not found"));

        Forms form = new Forms();
        applyFields(form, model);
        form.setBranch(branch);
        form.setUser(user);
        replacePainPoints(form, model.getPainPoints());

        return formsTransform.transform(formsRepository.save(form));
    }

    @Override
    public FormsModel getFormById(UUID id) {
        Forms form = formsRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Form not found"));
        assertCanAccess(form);
        return formsTransform.transform(form);
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

        return forms.stream().map(formsTransform::transform).collect(Collectors.toList());
    }

    @Override
    public FormsModel updateForm(UUID id, FormsModel model) {
        Forms form = formsRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Form not found"));
        assertCanAccess(form);

        // applyFields deliberately does not touch user or branch — ownership is
        // set once, at creation, and cannot be reassigned by an update.
        applyFields(form, model);
        replacePainPoints(form, model.getPainPoints());

        return formsTransform.transform(formsRepository.save(form));
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
