package com.hilotspa.backend.services;

import com.hilotspa.backend.entities.Forms;
import com.hilotspa.backend.model.FormsModel;
import com.hilotspa.backend.repository.FormsRepository;
import com.hilotspa.backend.transformer.FormsTransform;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class FormsServiceImpl implements FormsService {

    @Autowired
    private FormsRepository formsRepository;

    @Autowired
    private FormsTransform formsTransform;

    @Override
    public FormsModel createForm(FormsModel model) {
        Forms form = new Forms();
        form.setMainComplaint(model.getMainComplaint());
        form.setMainComplaintDuration(model.getMainComplaintDuration());
        form.setHasTherapy(model.isHasTherapy());
        form.setStatus(model.getStatus());
        form.setRemarks(model.getRemarks());
        // Note: Relationship mappings (User, PatientIntake, Branch) need to be set here via Repositories
        
        return formsTransform.transform(formsRepository.save(form));
    }

    @Override
    public FormsModel getFormById(Integer id) {
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
    public FormsModel updateForm(Integer id, FormsModel model) {
        Forms form = formsRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Form not found"));
        
        form.setMainComplaint(model.getMainComplaint());
        form.setMainComplaintDuration(model.getMainComplaintDuration());
        form.setHasTherapy(model.isHasTherapy());
        form.setStatus(model.getStatus());
        form.setRemarks(model.getRemarks());
        
        return formsTransform.transform(formsRepository.save(form));
    }
}