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

    /**
     * The branch's name, so the back-office chrome can label itself honestly.
     *
     * The sidebar used to read "Front desk - Bulan" for every staff account,
     * including Sorsogon's. On a demonstration of branch scoping that is the
     * worst possible defect: the screen contradicts the claim while showing the
     * correct data underneath it.
     */
    private String branchName;
}
