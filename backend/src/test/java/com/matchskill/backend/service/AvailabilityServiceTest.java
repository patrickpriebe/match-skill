package com.matchskill.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.matchskill.backend.dto.availability.AvailabilityWindowRequest;
import com.matchskill.backend.dto.availability.AvailabilityWindowResponse;
import com.matchskill.backend.entity.Availability;
import com.matchskill.backend.entity.User;
import com.matchskill.backend.exception.ApiException;
import com.matchskill.backend.repository.AvailabilityRepository;
import com.matchskill.backend.repository.UserRepository;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

    @Mock
    private AvailabilityRepository availabilityRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AvailabilityService availabilityService;

    @Test
    @DisplayName("getMyAvailability: maps entity list to DTO list")
    void shouldReturnAvailabilityWindows() {
        UUID userId = UUID.randomUUID();
        Availability window = Availability.builder()
                .id(UUID.randomUUID())
                .dayOfWeek(DayOfWeek.MONDAY)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(12, 0))
                .build();
        when(availabilityRepository.findByUserId(userId)).thenReturn(List.of(window));

        List<AvailabilityWindowResponse> result = availabilityService.getMyAvailability(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(result.get(0).startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(result.get(0).endTime()).isEqualTo(LocalTime.of(12, 0));
    }

    @Test
    @DisplayName("replaceMyAvailability: validates windows, deletes old, saves new")
    void shouldReplaceAvailabilityWindowsSuccessfully() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

        List<AvailabilityWindowRequest> requests = List.of(
                new AvailabilityWindowRequest(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0)),
                new AvailabilityWindowRequest(DayOfWeek.FRIDAY, LocalTime.of(14, 0), LocalTime.of(18, 0)));

        availabilityService.replaceMyAvailability(userId, requests);

        verify(availabilityRepository).deleteByUserId(userId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Availability>> captor = ArgumentCaptor.forClass(List.class);
        verify(availabilityRepository).saveAll(captor.capture());
        List<Availability> saved = captor.getValue();

        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(saved.get(1).getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
    }

    @Test
    @DisplayName("replaceMyAvailability: throws 400 if startTime is not before endTime")
    void shouldThrowBadRequestWhenStartTimeEqualOrAfterEndTime() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

        // Equal start and end
        List<AvailabilityWindowRequest> equalTimes = List.of(
                new AvailabilityWindowRequest(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(10, 0)));

        assertThatThrownBy(() -> availabilityService.replaceMyAvailability(userId, equalTimes))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getCode()).isEqualTo("INVALID_AVAILABILITY_WINDOW");
                });

        // Start after end
        List<AvailabilityWindowRequest> invertedTimes = List.of(
                new AvailabilityWindowRequest(DayOfWeek.MONDAY, LocalTime.of(15, 0), LocalTime.of(10, 0)));

        assertThatThrownBy(() -> availabilityService.replaceMyAvailability(userId, invertedTimes))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getCode()).isEqualTo("INVALID_AVAILABILITY_WINDOW");
                });

        verify(availabilityRepository, never()).deleteByUserId(any());
        verify(availabilityRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("replaceMyAvailability: throws 404 when user does not exist")
    void shouldThrowNotFoundWhenUserMissing() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> availabilityService.replaceMyAvailability(userId, List.of()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getCode()).isEqualTo("USER_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("Regression P1: reject overlapping availability windows on the same day")
    void shouldRejectOverlappingWindowsOnSameDay() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

        List<AvailabilityWindowRequest> overlapping = List.of(
                new AvailabilityWindowRequest(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0)),
                new AvailabilityWindowRequest(DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(14, 0)));

        assertThatThrownBy(() -> availabilityService.replaceMyAvailability(userId, overlapping))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getCode()).isEqualTo("OVERLAPPING_AVAILABILITY_WINDOW");
                    assertThat(apiEx.getMessage()).contains("Overlapping windows on MONDAY");
                });

        verify(availabilityRepository, never()).deleteByUserId(any());
        verify(availabilityRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Allow adjacent non-overlapping windows on same day and same hours on different days")
    void shouldAllowAdjacentWindowsAndDifferentDays() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

        // 09:00-12:00 and 12:00-15:00 are adjacent (not overlapping)
        List<AvailabilityWindowRequest> validWindows = List.of(
                new AvailabilityWindowRequest(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0)),
                new AvailabilityWindowRequest(DayOfWeek.MONDAY, LocalTime.of(12, 0), LocalTime.of(15, 0)),
                new AvailabilityWindowRequest(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(12, 0)));

        availabilityService.replaceMyAvailability(userId, validWindows);

        verify(availabilityRepository).deleteByUserId(userId);
        verify(availabilityRepository).saveAll(any());
    }
}
