package com.matchskill.backend.dto.skill;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SuggestSkillRequest(@NotBlank @Size(max = 100) String name) {}
