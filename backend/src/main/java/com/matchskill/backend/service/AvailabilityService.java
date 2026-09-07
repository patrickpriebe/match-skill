package com.matchskill.backend.service;

import com.matchskill.backend.dto.availability.AvailabilityWindowRequest;
import com.matchskill.backend.dto.availability.AvailabilityWindowResponse;
import com.matchskill.backend.entity.Availability;
import com.matchskill.backend.entity.User;
import com.matchskill.backend.exception.ApiException;
import com.matchskill.backend.repository.AvailabilityRepository;
import com.matchskill.backend.repository.UserRepository;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AvailabilityService {

    private final AvailabilityRepository availabilityRepository;
    private final UserRepository userRepository;

    public AvailabilityService(
            AvailabilityRepository availabilityRepository, UserRepository userRepository) {
        this.availabilityRepository = availabilityRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<AvailabilityWindowResponse> getMyAvailability(UUID userId) {
        return availabilityRepository.findByUserId(userId).stream()
                .map(AvailabilityWindowResponse::from)
                .toList();
    }

    @Transactional
    public List<AvailabilityWindowResponse> replaceMyAvailability(
            UUID userId, List<AvailabilityWindowRequest> windows) {
        return replaceMyAvailability(userId, windows, null);
    }

    @Transactional
    public List<AvailabilityWindowResponse> replaceMyAvailability(
            UUID userId, List<AvailabilityWindowRequest> windows, String timeZone) {
        User user =
                userRepository
                        .findByIdForUpdate(userId)
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

        if (timeZone != null && !ZoneId.getAvailableZoneIds().contains(timeZone)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "timeZone must be a valid IANA time zone");
        }
        for (AvailabilityWindowRequest window : windows) {
            if (!window.startTime().isBefore(window.endTime())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_AVAILABILITY_WINDOW",
                        "startTime must be before endTime for " + window.dayOfWeek());
            }
        }
        rejectOverlaps(windows);

        if (timeZone != null) {
            user.setTimeZone(timeZone);
        }
        availabilityRepository.deleteByUserId(userId);

        List<Availability> entries =
                windows.stream()
                        .map(
                                window ->
                                        Availability.builder()
                                                .user(user)
                                                .dayOfWeek(window.dayOfWeek())
                                                .startTime(window.startTime())
                                                .endTime(window.endTime())
                                                .build())
                        .toList();
        availabilityRepository.saveAll(entries);

        return getMyAvailability(userId);
    }

    /** Two windows on the same day that share any minute would make "am I free then?" ambiguous. */
    private void rejectOverlaps(List<AvailabilityWindowRequest> windows) {
        Map<DayOfWeek, List<AvailabilityWindowRequest>> byDay =
                windows.stream().collect(Collectors.groupingBy(AvailabilityWindowRequest::dayOfWeek));
        for (var entry : byDay.entrySet()) {
            List<AvailabilityWindowRequest> sorted =
                    entry.getValue().stream()
                            .sorted(Comparator.comparing(AvailabilityWindowRequest::startTime))
                            .toList();
            for (int i = 1; i < sorted.size(); i++) {
                if (sorted.get(i).startTime().isBefore(sorted.get(i - 1).endTime())) {
                    throw new ApiException(
                            HttpStatus.BAD_REQUEST,
                            "OVERLAPPING_AVAILABILITY_WINDOW",
                            "Overlapping windows on " + entry.getKey());
                }
            }
        }
    }
}
