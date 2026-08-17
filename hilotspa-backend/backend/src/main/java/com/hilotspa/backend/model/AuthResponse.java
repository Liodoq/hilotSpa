package com.hilotspa.backend.model;

import java.util.UUID;

import com.hilotspa.backend.entities.Role;

import lombok.Data;

/** What the client gets back after a successful register or login. */
@Data
public class AuthResponse {
    private String token;
    private long expiresInSeconds;

    private UUID userId;
    private String email;
    private String fullName;
    private Role role;

    /** Null for customers and administrators; set for branch staff. */
    private UUID branchId;
}
