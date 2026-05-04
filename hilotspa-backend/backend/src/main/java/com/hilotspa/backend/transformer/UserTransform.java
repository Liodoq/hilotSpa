package com.hilotspa.backend.transformer;

import com.hilotspa.backend.entities.*;
import com.hilotspa.backend.model.*;
public interface UserTransform {
    UserModel transform(User userEntity);
    User transform(UserModel userModel);
}