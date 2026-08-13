package com.hilotspa.backend.services;

import java.util.List;
import java.util.UUID;

import com.hilotspa.backend.model.MassageModel;

public interface MassageService {
    MassageModel createMassage(MassageModel massageModel);
    MassageModel getMassageById(UUID massageId);
    MassageModel updateMassage(UUID massageId, MassageModel massageModel);
    List<MassageModel> getAllMassages();
}
