package com.matchskill.backend.dto.feedback;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateFeedbackRequest(@NotNull @Min(1) @Max(5) Integer rating, String comment) {}
