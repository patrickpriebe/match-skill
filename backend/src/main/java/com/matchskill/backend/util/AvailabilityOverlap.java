package com.matchskill.backend.util;

import com.matchskill.backend.entity.Availability;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Compares two users' recurring weekly availability windows even when their
 * time zones differ, by converting each local window into UTC minute-of-week
 * ranges (0..10079) on a fixed reference week, then intersecting ranges.
 */
public final class AvailabilityOverlap {

    private static final int MINUTES_PER_WEEK = 7 * 24 * 60;
    // An arbitrary Monday used only to anchor day-of-week -> date conversion.
    private static final LocalDate REFERENCE_MONDAY = LocalDate.of(2024, 1, 1);

    private record MinuteRange(int start, int end) {}

    private AvailabilityOverlap() {}

    /** True if any window of a overlaps any window of b, once both are expressed in UTC. */
    public static boolean overlaps(
            List<Availability> a, String zoneA, List<Availability> b, String zoneB) {
        return overlapMinutes(a, zoneA, b, zoneB) > 0;
    }

    /** Total minutes per reference week during which a and b are both free, once expressed in UTC. */
    public static int overlapMinutes(
            List<Availability> a, String zoneA, List<Availability> b, String zoneB) {
        List<MinuteRange> rangesA = toUtcMinuteRanges(a, zoneA);
        List<MinuteRange> rangesB = toUtcMinuteRanges(b, zoneB);
        int total = 0;
        for (MinuteRange ra : rangesA) {
            for (MinuteRange rb : rangesB) {
                int start = Math.max(ra.start(), rb.start());
                int end = Math.min(ra.end(), rb.end());
                if (start < end) {
                    total += end - start;
                }
            }
        }
        return total;
    }

    private static List<MinuteRange> toUtcMinuteRanges(List<Availability> windows, String zoneId) {
        ZoneId zone = ZoneId.of(zoneId);
        List<MinuteRange> ranges = new ArrayList<>();
        for (Availability window : windows) {
            LocalDate date = REFERENCE_MONDAY.plusDays(window.getDayOfWeek().getValue() - 1L);
            ZonedDateTime startUtc =
                    ZonedDateTime.of(date, window.getStartTime(), zone).withZoneSameInstant(ZoneOffset.UTC);
            ZonedDateTime endUtc =
                    ZonedDateTime.of(date, window.getEndTime(), zone).withZoneSameInstant(ZoneOffset.UTC);

            int startMinute = minuteOfWeek(startUtc.getDayOfWeek(), startUtc.toLocalTime());
            int endMinute = minuteOfWeek(endUtc.getDayOfWeek(), endUtc.toLocalTime());

            if (endMinute > startMinute) {
                ranges.add(new MinuteRange(startMinute, endMinute));
            } else if (endMinute < startMinute) {
                ranges.add(new MinuteRange(startMinute, MINUTES_PER_WEEK));
                ranges.add(new MinuteRange(0, endMinute));
            }
            // endMinute == startMinute: zero-length after conversion, contributes nothing.
        }
        return ranges;
    }

    private static int minuteOfWeek(DayOfWeek dayOfWeek, LocalTime time) {
        return (dayOfWeek.getValue() - 1) * 24 * 60 + time.toSecondOfDay() / 60;
    }
}
