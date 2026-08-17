package com.hilotspa.backend.services;

import com.hilotspa.backend.model.AuthResponse;
import com.hilotspa.backend.model.LoginRequest;
import com.hilotspa.backend.model.RegisterRequest;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
}
