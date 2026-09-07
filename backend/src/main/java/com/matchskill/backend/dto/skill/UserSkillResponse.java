package com.matchskill.backend.dto.skill;

import com.matchskill.backend.entity.SkillDirection;
import com.matchskill.backend.entity.UserSkill;
import java.util.UUID;

public record UserSkillResponse(UUID id, SkillResponse skill, SkillDirection direction) {

    public static UserSkillResponse from(UserSkill userSkill) {
        return new UserSkillResponse(
                userSkill.getId(), SkillResponse.from(userSkill.getSkill()), userSkill.getDirection());
    }
}
