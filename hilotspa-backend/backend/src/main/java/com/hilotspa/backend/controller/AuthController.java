package com.hilotspa.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hilotspa.backend.model.AuthResponse;
import com.hilotspa.backend.model.LoginRequest;
import com.hilotspa.backend.model.PasswordDtos.ForgotRequest;
import com.hilotspa.backend.model.PasswordDtos.ForgotResult;
import com.hilotspa.backend.model.PasswordDtos.ResetRequest;
import com.hilotspa.backend.model.RegisterRequest;
import com.hilotspa.backend.services.AuthService;
import com.hilotspa.backend.services.PasswordResetService;

/**
 * The only unauthenticated endpoints in the system.
 * SecurityConfig permits /api/v1/auth/** ; everything else needs a token.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private PasswordResetService passwordResetService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        return new ResponseEntity<>(authService.register(request), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * "I forgot my password."
     *
     * Always 200, always the same sentence. An endpoint that answered 404 for
     * an address with no account would let anybody test whether a given person
     * is a client of this spa - which, for a place that keeps pain maps and
     * medical histories, is a health question wearing an account question's
     * clothes. The service walks the same path either way; see the note there.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotResult> forgot(@RequestBody ForgotRequest request) {
        return ResponseEntity.ok(passwordResetService.forgot(request));
    }

    /** The token out of that email, spent once, for a new password. */
    @PostMapping("/reset-password")
    public ResponseEntity<Void> reset(@RequestBody ResetRequest request) {
        passwordResetService.reset(request);
        return ResponseEntity.noContent().build();
    }
}
