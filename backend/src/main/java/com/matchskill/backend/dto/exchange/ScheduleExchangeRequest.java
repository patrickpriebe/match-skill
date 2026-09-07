package com.matchskill.backend.dto.exchange;

import com.matchskill.backend.validation.MeetingUrlConstraints;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record ScheduleExchangeRequest(
        @NotNull Instant scheduledAt,
        @NotBlank @Size(max = MeetingUrlConstraints.MAX_LENGTH) String meetingUrl) {}
