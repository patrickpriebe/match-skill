package com.matchskill.backend.dto.user;

import com.matchskill.backend.dto.availability.AvailabilityWindowResponse;
import com.matchskill.backend.dto.skill.SkillResponse;
import java.util.List;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String displayName,
        String bio,
        String timeZone,
        List<SkillResponse> skillsOffered,
        List<SkillResponse> skillsWanted,
        double reputationAverage,
        long reputationCount,
        List<AvailabilityWindowResponse> availability) {}
