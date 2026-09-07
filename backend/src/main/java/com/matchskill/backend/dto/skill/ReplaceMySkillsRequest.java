package com.matchskill.backend.dto.skill;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ReplaceMySkillsRequest(
        @NotNull List<@NotNull UUID> offeredSkillIds, @NotNull List<@NotNull UUID> wantedSkillIds) {}
