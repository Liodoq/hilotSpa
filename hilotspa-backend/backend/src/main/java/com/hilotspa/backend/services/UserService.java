package com.hilotspa.backend.services;

import com.hilotspa.backend.model.UserModel;
import java.util.List;

public interface UserService {
    UserModel createUser(UserModel userModel);
    UserModel getUserById(Integer id);
    List<UserModel> getAllUsers();
    UserModel updateUser(Integer id, UserModel userModel);
    void deleteUser(Integer id);
}