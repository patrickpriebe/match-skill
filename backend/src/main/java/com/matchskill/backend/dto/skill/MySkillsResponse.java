package com.matchskill.backend.dto.skill;

import java.util.List;

public record MySkillsResponse(List<UserSkillResponse> offered, List<UserSkillResponse> wanted) {}
