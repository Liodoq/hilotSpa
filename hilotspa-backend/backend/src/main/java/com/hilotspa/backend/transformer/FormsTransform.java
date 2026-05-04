package com.hilotspa.backend.transformer;

import com.hilotspa.backend.entities.*;
import com.hilotspa.backend.model.*;

public interface FormsTransform {
    FormsModel transform(Forms formsEntity);
    Forms transform(FormsModel formsModel);
}