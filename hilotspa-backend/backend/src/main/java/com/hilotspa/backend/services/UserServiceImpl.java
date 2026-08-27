package com.hilotspa.backend.services;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.hilotspa.backend.config.CurrentUser;
import com.hilotspa.backend.entities.Branch;
import com.hilotspa.backend.entities.Role;
import com.hilotspa.backend.entities.User;
import com.hilotspa.backend.model.AccountDtos.ChangePassword;
import com.hilotspa.backend.model.AccountDtos.Me;
import com.hilotspa.backend.model.AccountDtos.UpdateMe;
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

    // ------------------------------------------------------------------
    // Self-service. The account is identified by the token, never by an id in
    // the path - otherwise every customer could edit every other customer.
    // ------------------------------------------------------------------

    private User currentOrThrow() {
        java.util.UUID id = CurrentUser.id().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
        return userRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    @Override
    public Me me() {
        return toMe(currentOrThrow());
    }

    @Override
    public Me updateMe(UpdateMe body) {
        User u = currentOrThrow();

        if (body.email() != null && !body.email().isBlank()) {
            String email = body.email().trim().toLowerCase();
            if (!email.equals(u.getEmail())) {
                // Same 409 as registration. Silently keeping the old address
                // would look like a save that worked.
                userRepository.findUserByEmail(email).ifPresent(other -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "That email is already registered");
                });
                u.setEmail(email);
            }
        }
        if (body.firstName() != null && !body.firstName().isBlank()) u.setFirstName(body.firstName().trim());
        if (body.lastName() != null && !body.lastName().isBlank())   u.setLastName(body.lastName().trim());
        if (body.middleName() != null) u.setMiddleName(body.middleName().trim());
        if (body.contact() != null && !body.contact().isBlank())     u.setContact(body.contact().trim());
        if (body.address() != null && !body.address().isBlank())     u.setAddress(body.address().trim());

        return toMe(userRepository.save(u));
    }

    @Override
    public void changeMyPassword(ChangePassword body) {
        User u = currentOrThrow();

        if (body.newPassword() == null || body.newPassword().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The new password must be at least 8 characters");
        }
        // Proving you know the current one is what stops a borrowed phone from
        // becoming a stolen account.
        if (body.currentPassword() == null
                || !passwordEncoder.matches(body.currentPassword(), u.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Your current password is not correct");
        }
        u.setPasswordHash(passwordEncoder.encode(body.newPassword()));
        userRepository.save(u);
    }

    private Me toMe(User u) {
        return new Me(u.getId(), u.getFirstName(), u.getMiddleName(), u.getLastName(),
                u.getContact(), u.getAddress(), u.getEmail(),
                u.getRole() == null ? null : u.getRole().name());
    }
}
