package com.hilotspa.backend.services;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.hilotspa.backend.entities.Branch;
import com.hilotspa.backend.entities.Role;
import com.hilotspa.backend.entities.User;
import com.hilotspa.backend.model.UserModel;
import com.hilotspa.backend.repository.BranchRepository;
import com.hilotspa.backend.repository.UserRepository;
import com.hilotspa.backend.transformer.UserTransform;

/**
 * Administrative user management. This is how STAFF and ADMIN accounts are created —
 * public self-registration (AuthService) can only ever produce a CUSTOMER.
 */
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserTransform userTransform;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public UserModel createUser(UserModel userModel) {
        if (userModel.getPassword() == null || userModel.getPassword().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be at least 8 characters");
        }
        String email = userModel.getEmail() == null ? "" : userModel.getEmail().trim().toLowerCase();
        if (userRepository.findUserByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That email is already registered");
        }

        User user = new User();
        applyProfile(user, userModel, email);
        user.setPasswordHash(passwordEncoder.encode(userModel.getPassword()));
        user.setEnabled(userModel.isEnabled());

        return userTransform.transform(userRepository.save(user));
    }

    @Override
    public UserModel getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return userTransform.transform(user);
    }

    @Override
    public List<UserModel> getAllUsers() {
        return userRepository.findAll().stream()
                .map(userTransform::transform)
                .collect(Collectors.toList());
    }

    @Override
    public UserModel updateUser(UUID id, UserModel userModel) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        String email = userModel.getEmail() == null ? user.getEmail()
                : userModel.getEmail().trim().toLowerCase();
        applyProfile(user, userModel, email);
        user.setEnabled(userModel.isEnabled());

        // Only re-hash when a new password was actually supplied. A blank field on an
        // edit form means "leave it alone", not "wipe the password".
        if (userModel.getPassword() != null && !userModel.getPassword().isBlank()) {
            if (userModel.getPassword().length() < 8) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Password must be at least 8 characters");
            }
            user.setPasswordHash(passwordEncoder.encode(userModel.getPassword()));
        }

        return userTransform.transform(userRepository.save(user));
    }

    @Override
    public void deleteUser(UUID id) {
        userRepository.deleteById(id);
    }

    private void applyProfile(User user, UserModel model, String email) {
        user.setLastName(model.getLastName());
        user.setFirstName(model.getFirstName());
        user.setMiddleName(model.getMiddleName());
        user.setContact(model.getContact());
        user.setAddress(model.getAddress());
        user.setEmail(email);
        user.setRole(model.getRole());

        // A STAFF account is meaningless without a branch — that FK is what scopes
        // their queries under Process Rule #5.
        if (model.getRole() == Role.STAFF && model.getBranchId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Branch staff must be assigned to a branch");
        }

        if (model.getBranchId() != null) {
            Branch branch = branchRepository.findById(model.getBranchId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Branch not found"));
            user.setBranch(branch);
        } else {
            user.setBranch(null);
        }
    }
}
