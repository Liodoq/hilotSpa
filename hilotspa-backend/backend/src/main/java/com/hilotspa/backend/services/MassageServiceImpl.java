package com.hilotspa.backend.services;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hilotspa.backend.entities.Massage;
import com.hilotspa.backend.model.MassageModel;
import com.hilotspa.backend.repository.MassageRepository;
import com.hilotspa.backend.transformer.MassageTransform;

@Service
public class MassageServiceImpl implements MassageService {

    @Autowired
    private MassageRepository massageRepository;

    @Autowired
    private MassageTransform massageTransform;

    @Override
    public MassageModel createMassage(MassageModel massageModel) {
        Massage massage = new Massage();
        massage.setName(massageModel.getName());
        massage.setDurationMinute(massageModel.getDurationMinute());
        massage.setPrice(massageModel.getPrice());
        return massageTransform.transform(massageRepository.save(massage));
    }

    @Override
    public MassageModel getMassageById(UUID massageId) {
        Massage massage = massageRepository.findById(massageId)
                .orElseThrow(() -> new RuntimeException("Massage not found"));
        return massageTransform.transform(massage);
    }

    @Override
    public MassageModel updateMassage(UUID massageId, MassageModel massageModel) {
        Massage massage = massageRepository.findById(massageId)
                .orElseThrow(() -> new RuntimeException("Massage not found"));

        massage.setName(massageModel.getName());
        massage.setDurationMinute(massageModel.getDurationMinute());
        massage.setPrice(massageModel.getPrice());

        return massageTransform.transform(massageRepository.save(massage));
    }

    @Override
    public List<MassageModel> getAllMassages() {
        return massageRepository.findAll().stream()
                .map(massageTransform::transform)
                .collect(Collectors.toList());
    }
}
