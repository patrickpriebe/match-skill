package com.matchskill.backend.dto.availability;

import com.matchskill.backend.entity.Availability;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

public record AvailabilityWindowResponse(
        UUID id, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {

    public static AvailabilityWindowResponse from(Availability availability) {
        return new AvailabilityWindowResponse(
                availability.getId(),
                availability.getDayOfWeek(),
                availability.getStartTime(),
                availability.getEndTime());
    }
}
