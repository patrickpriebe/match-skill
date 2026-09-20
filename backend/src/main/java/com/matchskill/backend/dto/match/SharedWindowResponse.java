package com.matchskill.backend.dto.match;

import com.matchskill.backend.util.AvailabilityOverlap.SharedWindow;
import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * One stretch during which both people are free, already converted to the
 * asking user's own time zone. The conversion happens here rather than in the
 * browser because it is the server that knows the other person's zone, and
 * sending two zones plus two window lists would ask every client to
 * re-implement the intersection.
 */
public record SharedWindowResponse(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, int minutes) {

    public static SharedWindowResponse from(SharedWindow window) {
        return new SharedWindowResponse(
                window.dayOfWeek(), window.startTime(), window.endTime(), window.minutes());
    }
}
