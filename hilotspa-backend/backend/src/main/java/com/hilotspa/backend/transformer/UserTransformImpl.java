package com.hilotspa.backend.transformer;

import org.springframework.stereotype.Component;
import com.hilotspa.backend.entities.*;
import com.hilotspa.backend.model.*;

@Component
public class UserTransformImpl implements UserTransform {
    @Override
    public UserModel transform(User userEntity) {
        if (userEntity == null) return null;
        UserModel userModel = new UserModel();
        userModel.setId(userEntity.getId());
        userModel.setLastName(userEntity.getLastName());
        userModel.setFirstName(userEntity.getFirstName());
        userModel.setMiddleName(userEntity.getMiddleName());
        userModel.setContact(userEntity.getContact());
        userModel.setAddress(userEntity.getAddress());
        userModel.setEmail(userEntity.getEmail());
        userModel.setRole(userEntity.getRole());
        userModel.setCreatedAt(userEntity.getCreatedAt());
        return userModel;
    }

    @Override
    public User transform(UserModel userModel) {
        if (userModel == null) return null;
        User userEntity = new User();
        userEntity.setId(userModel.getId());
        userEntity.setLastName(userModel.getLastName());
        userEntity.setFirstName(userModel.getFirstName());
        userEntity.setMiddleName(userModel.getMiddleName());
        userEntity.setContact(userModel.getContact());
        userEntity.setAddress(userModel.getAddress());
        userEntity.setEmail(userModel.getEmail());
        userEntity.setRole(userModel.getRole());
        return userEntity;
    }
}
