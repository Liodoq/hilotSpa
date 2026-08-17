package com.hilotspa.backend.model;

import java.time.LocalDateTime;
import java.util.UUID;

import com.hilotspa.backend.entities.Role;

import lombok.Data;

@Data
public class UserModel {
    private UUID id;

    private String lastName;

    private String firstName;

    private String middleName;

    private String contact;

    private String address;

    private String email;

    private Role role;

    private UUID branchId;

    private boolean enabled = true;

    /**
     * WRITE-ONLY. Accepted when an administrator creates or resets an account.
     * UserTransform never populates it, so it is always null on the way out —
     * a password must never travel back to a client.
     */
    private String password;

    private LocalDateTime createdAt;
}
