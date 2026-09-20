package com.matchskill.backend.dto.match;

import com.matchskill.backend.dto.skill.SkillResponse;
import com.matchskill.backend.entity.ExchangeStrength;
import java.util.List;
import java.util.UUID;

/**
 * Everything a match card needs, in one response.
 *
 * <p>This used to carry only the identity, the strength and the rating, which
 * left the browser fetching a full profile per row to find the time zone and
 * the two skill lists — twelve extra round trips for one page of results.
 * Worse, {@code overlapMinutes} was computed by the ranking and then thrown
 * away, so the signal that decides the order was the one thing the person
 * could never see.
 */
public record MatchResponse(
        UUID userId,
        String displayName,
        String bio,
        String timeZone,
        ExchangeStrength strength,
        double reputationAverage,
        long reputationCount,
        List<SkillResponse> offeredSkills,
        List<SkillResponse> wantedSkills,
        int overlapMinutes,
        List<SharedWindowResponse> sharedWindows) {}
