package com.matchskill.backend.dto.auth;

import java.util.UUID;

public record MeResponse(
        UUID id,
        String email,
        String displayName,
        String bio,
        String timeZone,
        boolean skillsRegistered) {}
