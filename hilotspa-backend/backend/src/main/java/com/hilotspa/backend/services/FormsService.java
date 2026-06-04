package com.hilotspa.backend.services;

import com.hilotspa.backend.model.FormsModel;
import java.util.List;

public interface FormsService {
    FormsModel createForm(FormsModel formsModel);
    FormsModel getFormById(Integer id);
    List<FormsModel> getAllForms();
    FormsModel updateForm(Integer id, FormsModel formsModel);
}