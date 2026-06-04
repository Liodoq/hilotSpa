package com.hilotspa.backend.services;

import com.hilotspa.backend.entities.Demographics;
import com.hilotspa.backend.model.DemographicsModel;
import com.hilotspa.backend.repository.DemographicsRepository;
import com.hilotspa.backend.transformer.DemographicsTransform;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DemographicsServiceImpl implements DemographicsService {

    @Autowired
    private DemographicsRepository demographicsRepository;

    @Autowired
    private DemographicsTransform demographicsTransform;

    @Override
    public DemographicsModel createDemographics(DemographicsModel model) {
        Demographics demographics = new Demographics();
        demographics.setAge(model.getAge());
        demographics.setSex(model.getSex());
        demographics.setStatus(model.getStatus());
        demographics.setHeight(model.getHeight());
        demographics.setWeight(model.getWeight());
        demographics.setBirthDate(model.getBirthDate());
        // Note: User mapping should be handled here via UserRepository if needed
        
        return demographicsTransform.transform(demographicsRepository.save(demographics));
    }

    @Override
    public DemographicsModel getDemographicsById(Integer id) {
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
    public DemographicsModel updateDemographics(Integer id, DemographicsModel model) {
        Demographics demographics = demographicsRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Demographics not found"));
        
        demographics.setAge(model.getAge());
        demographics.setSex(model.getSex());
        demographics.setStatus(model.getStatus());
        demographics.setHeight(model.getHeight());
        demographics.setWeight(model.getWeight());
        demographics.setBirthDate(model.getBirthDate());
        
        return demographicsTransform.transform(demographicsRepository.save(demographics));
    }
}