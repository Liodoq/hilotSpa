package com.hilotspa.backend.config;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import com.hilotspa.backend.entities.Role;

/**
 * Reads the caller's identity out of the validated JWT.
 *
 * Nothing here trusts the request body. The values come from a token this server
 * signed, so a client cannot claim to be staff at a branch they do not belong to.
 * This is what Process Rule #5 (branch-scoped access) is built on.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    private static Optional<Jwt> token() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return Optional.of(jwt);
        }
        return Optional.empty();
    }

    public static Optional<UUID> id() {
        return token().map(Jwt::getSubject).map(UUID::fromString);
    }

    public static Optional<String> email() {
        return token().map(jwt -> jwt.getClaimAsString("email"));
    }

    public static Optional<Role> role() {
        // "roles" is a JSON array in the token, so read it as a list, not a string.
        return token()
                .map(jwt -> jwt.getClaimAsStringList("roles"))
                .filter(list -> list != null && !list.isEmpty())
                .map(list -> Role.valueOf(list.get(0)));
    }

    /** Empty for customers and administrators; present for branch staff. */
    public static Optional<UUID> branchId() {
        return token().map(jwt -> jwt.getClaimAsString("branchId")).map(UUID::fromString);
    }

    public static boolean hasRole(Role role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role.name()));
    }

    public static boolean isAdmin() {
        return hasRole(Role.ADMIN);
    }

    /**
     * Guard for branch-scoped reads and writes.
     * An administrator may act on any branch. Staff may act only on their own.
     */
    public static boolean canAccessBranch(UUID branchId) {
        if (isAdmin()) {
            return true;
        }
        return branchId().map(own -> own.equals(branchId)).orElse(false);
    }
}
