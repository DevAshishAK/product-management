package com.pcom.common.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class CurrentUser {
    private CurrentUser() {}

    public static Optional<JwtAuthenticationToken> token() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a instanceof JwtAuthenticationToken t ? Optional.of(t) : Optional.empty();
    }

    /** Subject of the JWT as UUID (user id). */
    public static UUID id() {
        return token().map(t -> UUID.fromString(t.getToken().getSubject()))
                .orElseThrow(() -> new IllegalStateException("No authenticated user"));
    }

    public static boolean hasRole(String role) {
        return token().map(t -> t.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("ROLE_" + role))).orElse(false);
    }

    /** ADMIN users and SERVICE accounts may see other users' resources. */
    public static boolean isPrivileged() {
        return hasRole("ADMIN") || hasRole("SERVICE");
    }
}
