package com.matchskill.backend.dto.skill;

import com.matchskill.backend.entity.Skill;
import com.matchskill.backend.entity.SkillStatus;
import java.util.UUID;

public record SkillResponse(UUID id, String name, String slug, SkillStatus status) {

    public static SkillResponse from(Skill skill) {
        return new SkillResponse(skill.getId(), skill.getName(), skill.getSlug(), skill.getStatus());
    }
}
