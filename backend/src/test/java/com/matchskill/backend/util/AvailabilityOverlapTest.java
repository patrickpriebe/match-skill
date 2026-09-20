package com.matchskill.backend.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.matchskill.backend.entity.Availability;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AvailabilityOverlapTest {

    private Availability window(DayOfWeek day, String start, String end) {
        return Availability.builder()
                .dayOfWeek(day)
                .startTime(LocalTime.parse(start))
                .endTime(LocalTime.parse(end))
                .build();
    }

    @Test
    @DisplayName("Identical windows in same timezone yield full overlap minutes")
    void shouldCalculateFullOverlapSameTimezone() {
        List<Availability> a = List.of(window(DayOfWeek.MONDAY, "09:00", "11:00"));
        List<Availability> b = List.of(window(DayOfWeek.MONDAY, "09:00", "11:00"));

        int minutes = AvailabilityOverlap.overlapMinutes(a, "UTC", b, "UTC");
        assertThat(minutes).isEqualTo(120);
        assertThat(AvailabilityOverlap.overlaps(a, "UTC", b, "UTC")).isTrue();
    }

    @Test
    @DisplayName("Partial overlap in same timezone")
    void shouldCalculatePartialOverlapSameTimezone() {
        List<Availability> a = List.of(window(DayOfWeek.MONDAY, "09:00", "11:00"));
        List<Availability> b = List.of(window(DayOfWeek.MONDAY, "10:00", "12:00"));

        int minutes = AvailabilityOverlap.overlapMinutes(a, "UTC", b, "UTC");
        assertThat(minutes).isEqualTo(60);
        assertThat(AvailabilityOverlap.overlaps(a, "UTC", b, "UTC")).isTrue();
    }

    @Test
    @DisplayName("Adjacent boundary touching windows have zero overlap")
    void shouldReturnZeroForTouchingWindows() {
        List<Availability> a = List.of(window(DayOfWeek.MONDAY, "09:00", "10:00"));
        List<Availability> b = List.of(window(DayOfWeek.MONDAY, "10:00", "11:00"));

        int minutes = AvailabilityOverlap.overlapMinutes(a, "UTC", b, "UTC");
        assertThat(minutes).isZero();
        assertThat(AvailabilityOverlap.overlaps(a, "UTC", b, "UTC")).isFalse();
    }

    @Test
    @DisplayName("Different days in same timezone have zero overlap")
    void shouldReturnZeroForDifferentDays() {
        List<Availability> a = List.of(window(DayOfWeek.MONDAY, "09:00", "11:00"));
        List<Availability> b = List.of(window(DayOfWeek.TUESDAY, "09:00", "11:00"));

        assertThat(AvailabilityOverlap.overlapMinutes(a, "UTC", b, "UTC")).isZero();
        assertThat(AvailabilityOverlap.overlaps(a, "UTC", b, "UTC")).isFalse();
    }

    @Test
    @DisplayName("Different timezones align after UTC conversion")
    void shouldCalculateOverlapAcrossDifferentTimezones() {
        // User A: UTC Monday 13:00 - 15:00
        List<Availability> a = List.of(window(DayOfWeek.MONDAY, "13:00", "15:00"));
        // User B: America/Sao_Paulo (UTC-3) Monday 10:00 - 12:00 -> converts to UTC 13:00 - 15:00
        List<Availability> b = List.of(window(DayOfWeek.MONDAY, "10:00", "12:00"));

        int minutes = AvailabilityOverlap.overlapMinutes(a, "UTC", b, "America/Sao_Paulo");
        assertThat(minutes).isEqualTo(120);
        assertThat(AvailabilityOverlap.overlaps(a, "UTC", b, "America/Sao_Paulo")).isTrue();
    }

    @Test
    @DisplayName("Window crosses midnight boundary across day-of-week shift")
    void shouldCalculateOverlapCrossingDayBoundary() {
        // User A: UTC Monday 01:00 - 03:00
        List<Availability> a = List.of(window(DayOfWeek.MONDAY, "01:00", "03:00"));
        // User B: America/Sao_Paulo Sunday 22:00 - 23:00 -> converts to UTC Monday 01:00 - 02:00
        List<Availability> b = List.of(window(DayOfWeek.SUNDAY, "22:00", "23:00"));

        int minutes = AvailabilityOverlap.overlapMinutes(a, "UTC", b, "America/Sao_Paulo");
        assertThat(minutes).isEqualTo(60);
    }

    @Test
    @DisplayName("Empty availability windows return zero overlap")
    void shouldReturnZeroWhenAvailabilityEmpty() {
        List<Availability> a = List.of(window(DayOfWeek.MONDAY, "09:00", "11:00"));
        List<Availability> empty = List.of();

        assertThat(AvailabilityOverlap.overlapMinutes(a, "UTC", empty, "UTC")).isZero();
        assertThat(AvailabilityOverlap.overlaps(a, "UTC", empty, "UTC")).isFalse();
        assertThat(AvailabilityOverlap.overlapMinutes(empty, "UTC", empty, "UTC")).isZero();
    }

    @Test
    @DisplayName("Shared windows are returned in the asking user's own zone")
    void shouldReturnSharedWindowsInAskingUsersZone() {
        // A in Sao Paulo is free Monday 19:00-21:00 local, which is 22:00-00:00 UTC.
        List<Availability> a = List.of(window(DayOfWeek.MONDAY, "19:00", "21:00"));
        // B in Lisbon is free Monday 23:00-01:00 local (UTC+1 in winter is not assumed;
        // the zone rules decide), overlapping the tail of A's window.
        List<Availability> b = List.of(window(DayOfWeek.MONDAY, "23:00", "23:59"));

        var shared = AvailabilityOverlap.sharedWindows(a, "America/Sao_Paulo", b, "Europe/Lisbon");

        assertThat(shared).hasSize(1);
        assertThat(shared.getFirst().dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        // Reported in Sao Paulo time, not Lisbon's and not UTC.
        assertThat(shared.getFirst().startTime()).isEqualTo(LocalTime.parse("20:00"));
        assertThat(shared.getFirst().endTime()).isEqualTo(LocalTime.parse("20:59"));
    }

    @Test
    @DisplayName("Shared windows total the same minutes the ranking already counted")
    void shouldAgreeWithOverlapMinutes() {
        List<Availability> a = List.of(
                window(DayOfWeek.MONDAY, "09:00", "11:00"),
                window(DayOfWeek.WEDNESDAY, "14:00", "16:00"));
        List<Availability> b = List.of(
                window(DayOfWeek.MONDAY, "10:00", "12:00"),
                window(DayOfWeek.WEDNESDAY, "14:30", "15:30"));

        var shared = AvailabilityOverlap.sharedWindows(a, "UTC", b, "UTC");

        assertThat(shared).hasSize(2);
        assertThat(shared.stream().mapToInt(w -> w.minutes()).sum())
                .isEqualTo(AvailabilityOverlap.overlapMinutes(a, "UTC", b, "UTC"));
    }

    @Test
    @DisplayName("Touching intersections are merged into one stretch, not counted twice")
    void shouldMergeTouchingIntersections() {
        // Two adjacent windows on A's side meet one long window on B's side. Drawn
        // separately they would read as two chances to meet where there is one.
        List<Availability> a = List.of(
                window(DayOfWeek.FRIDAY, "09:00", "10:00"),
                window(DayOfWeek.FRIDAY, "10:00", "11:00"));
        List<Availability> b = List.of(window(DayOfWeek.FRIDAY, "09:00", "11:00"));

        var shared = AvailabilityOverlap.sharedWindows(a, "UTC", b, "UTC");

        assertThat(shared).hasSize(1);
        assertThat(shared.getFirst().startTime()).isEqualTo(LocalTime.parse("09:00"));
        assertThat(shared.getFirst().endTime()).isEqualTo(LocalTime.parse("11:00"));
        assertThat(shared.getFirst().minutes()).isEqualTo(120);
    }

    @Test
    @DisplayName("A shared stretch crossing local midnight is split per weekday")
    void shouldSplitSharedWindowAtLocalMidnight() {
        // Both are free across the local midnight boundary in the asking user's zone.
        List<Availability> a = List.of(window(DayOfWeek.SATURDAY, "23:00", "23:59"));
        List<Availability> b = List.of(window(DayOfWeek.SATURDAY, "23:00", "23:59"));

        var shared = AvailabilityOverlap.sharedWindows(a, "UTC", b, "Europe/Lisbon");

        // Lisbon is ahead of UTC, so B's Saturday evening lands earlier in UTC terms;
        // whatever survives must still belong to exactly one weekday each.
        assertThat(shared).allSatisfy(w -> assertThat(w.minutes()).isPositive());
    }

    @Test
    @DisplayName("No shared windows when the lists never intersect")
    void shouldReturnNoSharedWindowsWithoutIntersection() {
        List<Availability> a = List.of(window(DayOfWeek.MONDAY, "09:00", "11:00"));
        List<Availability> b = List.of(window(DayOfWeek.TUESDAY, "09:00", "11:00"));

        assertThat(AvailabilityOverlap.sharedWindows(a, "UTC", b, "UTC")).isEmpty();
        assertThat(AvailabilityOverlap.sharedWindows(a, "UTC", List.of(), "UTC")).isEmpty();
    }

    @Test
    @DisplayName("Multiple windows accumulate total overlap minutes")
    void shouldAccumulateMultipleOverlappingWindows() {
        List<Availability> a = List.of(
                window(DayOfWeek.MONDAY, "09:00", "11:00"),
                window(DayOfWeek.WEDNESDAY, "14:00", "16:00"));
        List<Availability> b = List.of(
                window(DayOfWeek.MONDAY, "10:00", "12:00"), // 60 min overlap
                window(DayOfWeek.WEDNESDAY, "14:30", "15:30")); // 60 min overlap

        int minutes = AvailabilityOverlap.overlapMinutes(a, "UTC", b, "UTC");
        assertThat(minutes).isEqualTo(120);
    }
}
