package com.hilotspa.backend.services;

import java.util.UUID;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hilotspa.backend.config.CurrentUser;
import com.hilotspa.backend.entities.AuditLog;
import com.hilotspa.backend.entities.Forms;
import com.hilotspa.backend.entities.PatientIntake;
import com.hilotspa.backend.model.PatientIntakeModel;
import com.hilotspa.backend.repository.AuditLogRepository;
import com.hilotspa.backend.repository.PatientIntakeRepository;
import com.hilotspa.backend.repository.UserRepository;
import com.hilotspa.backend.transformer.PatientIntakeTransform;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PatientIntakeServiceImpl implements PatientIntakeService {

    @Autowired
    private PatientIntakeRepository patientIntakeRepository;

    @Autowired
    private PatientIntakeTransform patientIntakeTransform;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Value("${hilotspa.node.id:local-dev}")
    private String nodeId;


    @Override
    public PatientIntakeModel getPatientIntakeById(UUID id) {
        PatientIntake intake = patientIntakeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Patient Intake not found"));
        return patientIntakeTransform.transform(intake);
    }

    @Override
    @Transactional
    public PatientIntakeModel recordAfter(UUID id, Integer painScoreAfter) {
        if (painScoreAfter == null || painScoreAfter < 0 || painScoreAfter > 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "AFTER score must be between 0 and 10");
        }
        PatientIntake intake = patientIntakeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Patient intake not found"));

        Forms form = intake.getForm();
        if (form != null && form.getBranch() != null
                && !CurrentUser.canAccessBranch(form.getBranch().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your branch");
        }

        intake.setPainScoreAfter(painScoreAfter);
        PatientIntake saved = patientIntakeRepository.save(intake);

        try {
            AuditLog row = new AuditLog();
            row.setAction("OUTCOME_RECORDED");
            row.setEntityType("PatientIntake");
            row.setEntityId(saved.getId());
            row.setBranch(form == null ? null : form.getBranch());
            row.setOriginNodeId(nodeId);
            row.setDetails(saved.getAnatomicalRegion() + " " + saved.getSide()
                    + ": before " + saved.getPainScoreBefore() + " -> after " + painScoreAfter);
            CurrentUser.id().flatMap(userRepository::findById).ifPresent(row::setActor);
            auditLogRepository.save(row);
        } catch (RuntimeException e) {
            log.warn("audit write failed for OUTCOME_RECORDED {} - {}", saved.getId(), e.toString());
        }

        return patientIntakeTransform.transform(saved);
    }

    @Override
    public List<PatientIntakeModel> getAllPatientIntakes() {
        return patientIntakeRepository.findAll().stream()
                .map(patientIntakeTransform::transform)
                .collect(Collectors.toList());
    }

}