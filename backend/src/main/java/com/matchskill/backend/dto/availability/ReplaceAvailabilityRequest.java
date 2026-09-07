package com.matchskill.backend.dto.availability;

import com.matchskill.backend.validation.ValidTimeZone;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ReplaceAvailabilityRequest(
        @NotNull @Valid List<@NotNull AvailabilityWindowRequest> windows,
        @ValidTimeZone @Size(max = 255)
                @Pattern(regexp = ".*\\S.*", message = "must not be blank") String timeZone) {}
