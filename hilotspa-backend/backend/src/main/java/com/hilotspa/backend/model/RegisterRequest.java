package com.hilotspa.backend.model;

import lombok.Data;

/**
 * Public self-registration payload.
 *
 * Deliberately has NO role and NO branchId field. This endpoint is unauthenticated,
 * so anything it accepts is attacker-controlled — a role field here would let anyone
 * register themselves as ADMIN. Every account created this way is a CUSTOMER.
 * Staff and administrator accounts are created by an administrator through
 * /api/v1/users, which requires a token.
 */
@Data
public class RegisterRequest {
    private String firstName;
    private String lastName;
    private String middleName;
    private String contact;
    private String address;
    private String email;
    private String password;
}
