package com.hilotspa.backend.services;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hilotspa.backend.entities.Branch;
import com.hilotspa.backend.entities.Forms;
import com.hilotspa.backend.entities.PatientIntake;
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
        Forms form = new Forms();
        applyFields(form, model);

        Branch branch = branchRepository.findById(model.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch not found"));
        User user = userRepository.findById(model.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        form.setBranch(branch);
        form.setUser(user);

        replacePainPoints(form, model.getPainPoints());

        return formsTransform.transform(formsRepository.save(form));
    }

    @Override
    public FormsModel getFormById(UUID id) {
        Forms form = formsRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Form not found"));
        return formsTransform.transform(form);
    }

    @Override
    public List<FormsModel> getAllForms() {
        return formsRepository.findAll().stream()
                .map(formsTransform::transform)
                .collect(Collectors.toList());
    }

    @Override
    public FormsModel updateForm(UUID id, FormsModel model) {
        Forms form = formsRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Form not found"));

        applyFields(form, model);
        replacePainPoints(form, model.getPainPoints());

        return formsTransform.transform(formsRepository.save(form));
    }

    private void applyFields(Forms form, FormsModel model) {
        form.setMainComplaint(model.getMainComplaint());
        form.setMainComplaintDuration(model.getMainComplaintDuration());
        form.setHasTherapy(model.isHasTherapy());
        form.setStatus(model.getStatus());
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
            point.setPainScore(pm.getPainScore());
            point.setComplaintType(pm.getComplaintType());
            form.getPainPoints().add(point);
        }
    }
}
