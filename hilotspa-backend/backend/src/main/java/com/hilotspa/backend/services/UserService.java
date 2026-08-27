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

    /** The caller's own account, from the token. */
    com.hilotspa.backend.model.AccountDtos.Me me();

    /** Update the caller's own account. Role and branch are not editable here. */
    com.hilotspa.backend.model.AccountDtos.Me updateMe(
            com.hilotspa.backend.model.AccountDtos.UpdateMe body);

    /** Change the caller's own password. Requires the current one. */
    void changeMyPassword(com.hilotspa.backend.model.AccountDtos.ChangePassword body);
}