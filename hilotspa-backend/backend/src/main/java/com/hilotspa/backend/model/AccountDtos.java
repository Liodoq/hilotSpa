package com.hilotspa.backend.model;

import java.util.UUID;

/** Self-service account editing. Identity always comes from the JWT. */
public final class AccountDtos {

    private AccountDtos() {
    }

    /** What the client may change about themselves. Role, branch and enabled
     *  are deliberately absent - they are not the account holder's to set. */
    public record UpdateMe(
            String firstName,
            String middleName,
            String lastName,
            String contact,
            String address,
            String email) {
    }

    public record ChangePassword(String currentPassword, String newPassword) {
    }

    public record Me(
            UUID id,
            String firstName,
            String middleName,
            String lastName,
            String contact,
            String address,
            String email,
            String role) {
    }
}
