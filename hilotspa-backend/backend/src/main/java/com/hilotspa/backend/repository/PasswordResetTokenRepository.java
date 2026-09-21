package com.hilotspa.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hilotspa.backend.entities.PasswordResetToken;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Every token ever issued for this account, newest first.
     *
     * Two callers: expiring the siblings after a successful reset, and the
     * throttle that refuses to mail the same address twice in a minute. Both
     * want the newest row, and a person has a handful of these in a lifetime,
     * so paging would be ceremony.
     */
    List<PasswordResetToken> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
