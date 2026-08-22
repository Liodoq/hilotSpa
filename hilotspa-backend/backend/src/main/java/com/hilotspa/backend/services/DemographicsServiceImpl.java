package com.hilotspa.backend.services;

import java.util.UUID;

import com.hilotspa.backend.config.CurrentUser;
import com.hilotspa.backend.entities.Demographics;
import com.hilotspa.backend.entities.User;
import com.hilotspa.backend.model.DemographicsModel;
import com.hilotspa.backend.repository.DemographicsRepository;
import com.hilotspa.backend.repository.UserRepository;
import com.hilotspa.backend.transformer.DemographicsTransform;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DemographicsServiceImpl implements DemographicsService {

    @Autowired
    private DemographicsRepository demographicsRepository;

    @Autowired
    private DemographicsTransform demographicsTransform;

    @Autowired
    private UserRepository userRepository;

    @Override
    public DemographicsModel createDemographics(DemographicsModel model) {
        Demographics demographics = new Demographics();
        demographics.setAge(model.getAge());
        demographics.setSex(model.getSex());
        demographics.setOccupation(model.getOccupation());
        demographics.setStatus(model.getStatus());
        demographics.setHeight(model.getHeight());
        demographics.setWeight(model.getWeight());
        demographics.setBirthDate(model.getBirthDate());
        // Note: User mapping should be handled here via UserRepository if needed
        
        return demographicsTransform.transform(demographicsRepository.save(demographics));
    }

    @Override
    public DemographicsModel getDemographicsById(UUID id) {
        Demographics demographics = demographicsRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Demographics not found"));
        return demographicsTransform.transform(demographics);
    }

    @Override
    public List<DemographicsModel> getAllDemographics() {
        return demographicsRepository.findAll().stream()
                .map(demographicsTransform::transform)
                .collect(Collectors.toList());
    }

    @Override
    public DemographicsModel updateDemographics(UUID id, DemographicsModel model) {
        Demographics demographics = demographicsRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Demographics not found"));
        
        demographics.setAge(model.getAge());
        demographics.setSex(model.getSex());
        demographics.setOccupation(model.getOccupation());
        demographics.setStatus(model.getStatus());
        demographics.setHeight(model.getHeight());
        demographics.setWeight(model.getWeight());
        demographics.setBirthDate(model.getBirthDate());
        
        return demographicsTransform.transform(demographicsRepository.save(demographics));
    }

    // ------------------------------------------------------------------
    // Self-service. The user comes from the validated token and never from
    // the request body, so a client cannot write onto someone else's row.
    // ------------------------------------------------------------------

    @Override
    public DemographicsModel saveMine(DemographicsModel model) {
        UUID userId = CurrentUser.id()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Not authenticated"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "User not found"));

        // Demographics is unique on users_id, so the second save must UPDATE.
        // A blind insert would throw the first time a client edits their profile.
        Demographics demographics = demographicsRepository.findByUserId(userId)
                .orElseGet(Demographics::new);

        applyFields(demographics, model);

        // B65: createDemographics never set this, so every row was written
        // orphaned with users_id NULL - invisible to findByUserId, and Postgres
        // lets unlimited NULLs through the unique constraint without complaining.
        demographics.setUser(user);

        return demographicsTransform.transform(demographicsRepository.save(demographics));
    }

    @Override
    public DemographicsModel getMine() {
        UUID userId = CurrentUser.id()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Not authenticated"));

        return demographicsRepository.findByUserId(userId)
                .map(demographicsTransform::transform)
                .orElse(null);
    }

    /** One copy of the field mapping, used by create, update and saveMine. */
    private void applyFields(Demographics entity, DemographicsModel model) {
        entity.setAge(model.getAge());
        entity.setSex(model.getSex());
        entity.setOccupation(model.getOccupation());
        entity.setStatus(model.getStatus());
        entity.setHeight(model.getHeight());
        entity.setWeight(model.getWeight());
        entity.setBirthDate(model.getBirthDate());
    }
}
