package com.hilotspa.backend.services;

import java.util.UUID;

import com.hilotspa.backend.model.PasswordDtos.AdminResetRequest;
import com.hilotspa.backend.model.PasswordDtos.AdminResetResult;
import com.hilotspa.backend.model.PasswordDtos.ForgotRequest;
import com.hilotspa.backend.model.PasswordDtos.ForgotResult;
import com.hilotspa.backend.model.PasswordDtos.ResetRequest;

public interface PasswordResetService {

    /** Unauthenticated. Always succeeds, and always says the same thing. */
    ForgotResult forgot(ForgotRequest body);

    /** Unauthenticated. The token out of the email, spent here. */
    void reset(ResetRequest body);

    /** ADMIN only. Set a temporary password, or send the account holder a link. */
    AdminResetResult adminReset(UUID userId, AdminResetRequest body);
}
