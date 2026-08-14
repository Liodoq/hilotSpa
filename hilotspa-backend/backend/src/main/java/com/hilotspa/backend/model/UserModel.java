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

    private LocalDateTime createdAt;
}