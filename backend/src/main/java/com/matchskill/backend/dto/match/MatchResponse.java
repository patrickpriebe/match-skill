package com.matchskill.backend.dto.match;

import com.matchskill.backend.entity.ExchangeStrength;
import java.util.UUID;

public record MatchResponse(
        UUID userId,
        String displayName,
        String bio,
        ExchangeStrength strength,
        double reputationAverage,
        long reputationCount) {}
