package com.hilotspa.backend.controller;

import java.util.UUID;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
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
import com.hilotspa.backend.model.UserModel;
import com.hilotspa.backend.services.UserService;

@RestController
@RequestMapping("/api/v1/users")
@CrossOrigin(origins = "http://localhost:4200")
public class UserController {

    @Autowired
    private UserService userService;

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

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.ok("User deleted successfully.");
    }
}