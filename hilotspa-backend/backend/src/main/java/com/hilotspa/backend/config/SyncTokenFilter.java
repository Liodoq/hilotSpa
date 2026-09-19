package com.hilotspa.backend.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Guards /api/v1/sync/** with a shared node secret (task 3.2).
 *
 * TWO THINGS THIS GETS RIGHT ON PURPOSE:
 *
 * 1. An unset token means CLOSED, never open. Both nodes publish 443 to the
 *    whole internet so Let's Encrypt can reach them, which means this endpoint
 *    is reachable by anyone who can resolve the hostname. A misconfigured node
 *    silently serving its peer API is the worst outcome available here, and
 *    "we forgot to set it" is much the likeliest way to arrive there.
 *
 * 2. MessageDigest.isEqual, not String.equals. A comparison that exits at the
 *    first differing byte leaks the token's length and matching prefix through
 *    response timing.
 */
@Component
public class SyncTokenFilter extends OncePerRequestFilter {

    @Value("${hilotspa.sync.token:}")
    private String token;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/sync/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        if (token == null || token.isBlank()) {
            deny(response, "Peer sync is not configured on this node.");
            return;
        }
        String offered = request.getHeader("X-Sync-Token");
        if (offered == null || !constantTimeEquals(offered, token)) {
            deny(response, "Bad or missing node token.");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    private static void deny(HttpServletResponse response, String why) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + why + "\"}");
    }
}
