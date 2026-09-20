package com.matchskill.backend.util;

import com.matchskill.backend.entity.Availability;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Compares two users' recurring weekly availability windows even when their
 * time zones differ, by converting each local window into UTC minute-of-week
 * ranges (0..10079) on a fixed reference week, then intersecting ranges.
 *
 * <p>{@link #sharedWindows} returns those intersections instead of summing
 * them, expressed back in the asking user's own zone. The total answers "how
 * well do we fit"; only the windows themselves answer "when", which is the
 * question the person actually has.
 */
public final class AvailabilityOverlap {

    private static final int MINUTES_PER_WEEK = 7 * 24 * 60;
    // An arbitrary Monday used only to anchor day-of-week -> date conversion.
    private static final LocalDate REFERENCE_MONDAY = LocalDate.of(2024, 1, 1);

    private record MinuteRange(int start, int end) {}

    /** One stretch of shared free time, already expressed in the asking user's zone. */
    public record SharedWindow(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {

        /** Minutes covered, counting a midnight end as the close of this day. */
        public int minutes() {
            int start = startTime.toSecondOfDay() / 60;
            int end = endTime.equals(LocalTime.MIDNIGHT) ? 24 * 60 : endTime.toSecondOfDay() / 60;
            return end - start;
        }
    }

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

    /**
     * The intersections themselves, in {@code zoneA}'s local time, ordered by
     * weekday. A shared stretch can straddle local midnight once the zones
     * differ, so intersections are split at the local day boundary: a heatmap
     * cell belongs to one weekday, and a window that silently spanned two
     * would have to be drawn in both.
     */
    public static List<SharedWindow> sharedWindows(
            List<Availability> a, String zoneA, List<Availability> b, String zoneB) {
        List<MinuteRange> intersections = new ArrayList<>();
        for (MinuteRange ra : toUtcMinuteRanges(a, zoneA)) {
            for (MinuteRange rb : toUtcMinuteRanges(b, zoneB)) {
                int start = Math.max(ra.start(), rb.start());
                int end = Math.min(ra.end(), rb.end());
                if (start < end) {
                    intersections.add(new MinuteRange(start, end));
                }
            }
        }
        List<SharedWindow> windows = new ArrayList<>();
        for (MinuteRange range : merge(intersections)) {
            windows.addAll(toLocalWindows(range, ZoneId.of(zoneA)));
        }
        windows.sort(Comparator.comparingInt((SharedWindow w) -> w.dayOfWeek().getValue())
                .thenComparing(SharedWindow::startTime));
        return windows;
    }

    /**
     * Two of one user's windows against one of the other's produce two
     * intersections that touch. Drawn separately they read as two chances to
     * meet where there is one continuous stretch, so they are merged first.
     */
    private static List<MinuteRange> merge(List<MinuteRange> ranges) {
        if (ranges.isEmpty()) {
            return List.of();
        }
        List<MinuteRange> sorted = new ArrayList<>(ranges);
        sorted.sort(Comparator.comparingInt(MinuteRange::start).thenComparingInt(MinuteRange::end));
        List<MinuteRange> merged = new ArrayList<>();
        MinuteRange current = sorted.getFirst();
        for (MinuteRange next : sorted.subList(1, sorted.size())) {
            if (next.start() <= current.end()) {
                current = new MinuteRange(current.start(), Math.max(current.end(), next.end()));
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    /** Projects one UTC minute-of-week range back into a zone, split per local weekday. */
    private static List<SharedWindow> toLocalWindows(MinuteRange range, ZoneId zone) {
        List<SharedWindow> windows = new ArrayList<>();
        int cursor = range.start();
        while (cursor < range.end()) {
            ZonedDateTime local = fromMinuteOfWeek(cursor).withZoneSameInstant(zone);
            LocalTime start = local.toLocalTime();
            int minutesLeftInLocalDay = 24 * 60 - start.toSecondOfDay() / 60;
            int take = Math.min(minutesLeftInLocalDay, range.end() - cursor);
            // Midnight closes this day rather than opening the next one.
            LocalTime end = take == minutesLeftInLocalDay ? LocalTime.MIDNIGHT : start.plusMinutes(take);
            windows.add(new SharedWindow(local.getDayOfWeek(), start, end));
            cursor += take;
        }
        return windows;
    }

    private static ZonedDateTime fromMinuteOfWeek(int minuteOfWeek) {
        return ZonedDateTime.of(REFERENCE_MONDAY, LocalTime.MIDNIGHT, ZoneOffset.UTC).plusMinutes(minuteOfWeek);
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
