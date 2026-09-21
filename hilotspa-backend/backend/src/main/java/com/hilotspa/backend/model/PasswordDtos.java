package com.hilotspa.backend.model;

/** Forgotten passwords: the public flow, and the front desk's version of it. */
public final class PasswordDtos {

    private PasswordDtos() {
    }

    /** Step one, unauthenticated: "this is my email address". */
    public record ForgotRequest(String email) {
    }

    /** Step two, unauthenticated: the token out of the email, and a new password. */
    public record ResetRequest(String token, String newPassword) {
    }

    /**
     * The answer to step one.
     *
     * One sentence, and the SAME sentence whether or not that address has an
     * account. A response that differs turns this endpoint into a way to ask
     * the system "does this person come here?" - which for a clinic is a
     * question about someone's health, not just their account.
     */
    public record ForgotResult(String message) {
    }

    /** What an administrator is asking for. */
    public record AdminResetRequest(
            /** TEMPORARY - set one now and read it out. EMAIL - send them the link. */
            String mode,
            /** Only for TEMPORARY. Blank means "generate one for me". */
            String temporaryPassword) {
    }

    /**
     * @param temporaryPassword shown to the administrator ONCE and never stored
     *                          in readable form; null for the EMAIL mode.
     */
    public record AdminResetResult(
            String mode,
            String temporaryPassword,
            String message) {
    }
}
