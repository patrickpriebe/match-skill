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
