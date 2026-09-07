package com.matchskill.backend.security;

import com.matchskill.backend.exception.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

/** Resolves the authenticated user's id set by {@link JwtAuthenticationFilter}. */
public final class CurrentUser {

    private CurrentUser() {}

    public static UUID id(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UUID userId)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "A valid bearer token is required");
        }
        return userId;
    }
}
