package com.hilotspa.backend.model;

import java.util.UUID;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserModel {
    private UUID id;

    private String lastName;

    private String firstName;

    private String middleName;

    private String contact;

    private String address;

    private String email;

    private String role;

    private LocalDateTime createdAt;
}