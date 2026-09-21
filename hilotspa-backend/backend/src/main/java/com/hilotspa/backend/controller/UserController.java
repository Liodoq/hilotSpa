package com.hilotspa.backend.controller;

import java.util.UUID;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hilotspa.backend.model.AccountDtos.ChangePassword;
import com.hilotspa.backend.model.AccountDtos.Me;
import com.hilotspa.backend.model.AccountDtos.UpdateMe;
import com.hilotspa.backend.model.PasswordDtos.AdminResetRequest;
import com.hilotspa.backend.model.PasswordDtos.AdminResetResult;
import com.hilotspa.backend.model.UserModel;
import com.hilotspa.backend.services.PasswordResetService;
import com.hilotspa.backend.services.UserService;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordResetService passwordResetService;

    // --- Self-service: the caller's own account, identified by the JWT -----
    // These sit under /users/me and are allowed for ANY signed-in role. The
    // rest of /users/** stays ADMIN, so a customer can edit themselves and
    // nobody else.

    @GetMapping("/me")
    public ResponseEntity<Me> me() {
        return ResponseEntity.ok(userService.me());
    }

    @PutMapping("/me")
    public ResponseEntity<Me> updateMe(@RequestBody UpdateMe body) {
        return ResponseEntity.ok(userService.updateMe(body));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changeMyPassword(@RequestBody ChangePassword body) {
        userService.changeMyPassword(body);
        return ResponseEntity.noContent().build();
    }

    // --- Administration ----------------------------------------------------

    @PostMapping("/create")
    public ResponseEntity<UserModel> createUser(@RequestBody UserModel userModel) {
        return new ResponseEntity<>(userService.createUser(userModel), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserModel> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @GetMapping
    public ResponseEntity<List<UserModel>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserModel> updateUser(@PathVariable UUID id, @RequestBody UserModel userModel) {
        return ResponseEntity.ok(userService.updateUser(id, userModel));
    }

    /**
     * The front desk helping somebody who is locked out.
     *
     * Two modes, because a spa has two versions of this person. TEMPORARY is
     * the one standing at the counter whose email is on a phone at home: the
     * administrator sets a password and reads it out, and it comes back in the
     * response ONCE - it is stored only as a BCrypt hash, so nothing can show
     * it again. EMAIL is the one on the telephone: the same link the public
     * flow sends, and the administrator never handles a password at all.
     *
     * Both land in the audit log under the administrator's name. Under
     * /users/** so SecurityConfig's hasRole("ADMIN") already covers it - note
     * that /users/me sits ABOVE that rule, which is why this cannot be reached
     * by the account holder themselves (they have /users/me/password, which
     * demands the current password).
     */
    @PostMapping("/{id}/password-reset")
    public ResponseEntity<AdminResetResult> adminResetPassword(
            @PathVariable UUID id, @RequestBody AdminResetRequest body) {
        return ResponseEntity.ok(passwordResetService.adminReset(id, body));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.ok("User deleted successfully.");
    }
}