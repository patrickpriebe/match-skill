package com.matchskill.backend.service;

import com.matchskill.backend.dto.availability.AvailabilityWindowResponse;
import com.matchskill.backend.dto.skill.SkillResponse;
import com.matchskill.backend.dto.user.UserProfileResponse;
import com.matchskill.backend.entity.SkillDirection;
import com.matchskill.backend.entity.User;
import com.matchskill.backend.exception.ApiException;
import com.matchskill.backend.repository.AvailabilityRepository;
import com.matchskill.backend.repository.UserRepository;
import com.matchskill.backend.repository.UserSkillRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {

    private final UserRepository userRepository;
    private final UserSkillRepository userSkillRepository;
    private final AvailabilityRepository availabilityRepository;
    private final ReputationService reputationService;

    public UserProfileService(
            UserRepository userRepository,
            UserSkillRepository userSkillRepository,
            AvailabilityRepository availabilityRepository,
            ReputationService reputationService) {
        this.userRepository = userRepository;
        this.userSkillRepository = userSkillRepository;
        this.availabilityRepository = availabilityRepository;
        this.reputationService = reputationService;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

        var offered =
                userSkillRepository.findByUserIdAndDirection(userId, SkillDirection.OFFERED).stream()
                        .map(us -> SkillResponse.from(us.getSkill()))
                        .toList();
        var wanted =
                userSkillRepository.findByUserIdAndDirection(userId, SkillDirection.WANTED).stream()
                        .map(us -> SkillResponse.from(us.getSkill()))
                        .toList();
        var availability =
                availabilityRepository.findByUserId(userId).stream()
                        .map(AvailabilityWindowResponse::from)
                        .toList();
        Reputation reputation = reputationService.of(userId);

        return new UserProfileResponse(
                user.getId(),
                user.getDisplayName(),
                user.getBio(),
                user.getTimeZone(),
                offered,
                wanted,
                reputation.average(),
                reputation.count(),
                availability);
    }
}
