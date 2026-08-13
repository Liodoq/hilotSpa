package com.hilotspa.backend.services;

import java.util.UUID;

import com.hilotspa.backend.model.UserModel;
import java.util.List;

public interface UserService {
    UserModel createUser(UserModel userModel);
    UserModel getUserById(UUID id);
    List<UserModel> getAllUsers();
    UserModel updateUser(UUID id, UserModel userModel);
    void deleteUser(UUID id);
}