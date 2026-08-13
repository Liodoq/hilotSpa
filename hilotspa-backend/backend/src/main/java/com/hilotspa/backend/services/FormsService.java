package com.hilotspa.backend.services;

import java.util.UUID;

import com.hilotspa.backend.model.FormsModel;
import java.util.List;

public interface FormsService {
    FormsModel createForm(FormsModel formsModel);
    FormsModel getFormById(UUID id);
    List<FormsModel> getAllForms();
    FormsModel updateForm(UUID id, FormsModel formsModel);
}