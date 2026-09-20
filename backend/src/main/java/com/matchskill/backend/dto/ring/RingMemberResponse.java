package com.matchskill.backend.dto.ring;

import com.matchskill.backend.dto.skill.SkillResponse;
import java.util.UUID;

/**
 * One person's place in a ring. {@code teaches} goes to the next member in the
 * list and {@code learns} comes from the previous one, wrapping at the end, so
 * the order of the list is the direction of the trade and nothing else has to
 * encode it.
 */
public record RingMemberResponse(
        UUID userId,
        String displayName,
        String timeZone,
        double reputationAverage,
        long reputationCount,
        SkillResponse teaches,
        SkillResponse learns) {}
