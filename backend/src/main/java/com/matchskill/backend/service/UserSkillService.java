package com.matchskill.backend.service;

import com.matchskill.backend.dto.skill.MySkillsResponse;
import com.matchskill.backend.dto.skill.UserSkillResponse;
import com.matchskill.backend.entity.Skill;
import com.matchskill.backend.entity.SkillDirection;
import com.matchskill.backend.entity.SkillStatus;
import com.matchskill.backend.entity.User;
import com.matchskill.backend.entity.UserSkill;
import com.matchskill.backend.exception.ApiException;
import com.matchskill.backend.repository.SkillRepository;
import com.matchskill.backend.repository.UserRepository;
import com.matchskill.backend.repository.UserSkillRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserSkillService {

    private final UserSkillRepository userSkillRepository;
    private final SkillRepository skillRepository;
    private final UserRepository userRepository;

    public UserSkillService(
            UserSkillRepository userSkillRepository,
            SkillRepository skillRepository,
            UserRepository userRepository) {
        this.userSkillRepository = userSkillRepository;
        this.skillRepository = skillRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public MySkillsResponse getMySkills(UUID userId) {
        List<UserSkill> offered = userSkillRepository.findByUserIdAndDirection(userId, SkillDirection.OFFERED);
        List<UserSkill> wanted = userSkillRepository.findByUserIdAndDirection(userId, SkillDirection.WANTED);
        return new MySkillsResponse(
                offered.stream().map(UserSkillResponse::from).toList(),
                wanted.stream().map(UserSkillResponse::from).toList());
    }

    /** Replaces both lists wholesale and completes first-time skill registration. */
    @Transactional
    public MySkillsResponse replaceMySkills(
            UUID userId, List<UUID> offeredSkillIds, List<UUID> wantedSkillIds) {
        User user =
                userRepository
                        .findByIdForUpdate(userId)
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

        Set<UUID> requestedIds = new LinkedHashSet<>(offeredSkillIds);
        requestedIds.addAll(wantedSkillIds);
        Map<UUID, Skill> skills = skillRepository.findAllById(requestedIds).stream()
                .collect(Collectors.toMap(Skill::getId, Function.identity()));

        List<UserSkill> entries =
                List.of(
                                createEntries(user, offeredSkillIds, SkillDirection.OFFERED, skills),
                                createEntries(user, wantedSkillIds, SkillDirection.WANTED, skills))
                        .stream()
                        .flatMap(List::stream)
                        .toList();
        userSkillRepository.deleteByUserId(userId);
        // Persist removals before inserting the same unique user/skill/direction keys.
        userSkillRepository.flush();
        userSkillRepository.saveAll(entries);

        user.setSkillsRegistered(true);
        userRepository.save(user);

        return getMySkills(userId);
    }

    @Transactional
    public void deleteMySkill(UUID userId, UUID userSkillId) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
        UserSkill userSkill =
                userSkillRepository
                        .findById(userSkillId)
                        .filter(entry -> entry.getUser().getId().equals(userId))
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "USER_SKILL_NOT_FOUND", "Entry not found"));
        userSkillRepository.delete(userSkill);
    }

    private List<UserSkill> createEntries(
            User user, List<UUID> skillIds, SkillDirection direction, Map<UUID, Skill> skills) {
        Set<UUID> uniqueIds = new LinkedHashSet<>(skillIds);
        return uniqueIds.stream()
                .map(
                        skillId -> {
                            Skill skill = skills.get(skillId);
                            if (skill == null) {
                                throw new ApiException(HttpStatus.BAD_REQUEST, "SKILL_NOT_FOUND",
                                        "Skill " + skillId + " does not exist");
                            }
                            if (skill.getStatus() != SkillStatus.APPROVED) {
                                throw new ApiException(
                                        HttpStatus.BAD_REQUEST,
                                        "SKILL_NOT_APPROVED",
                                        "Skill " + skillId + " is not approved yet");
                            }
                            return UserSkill.builder().user(user).skill(skill).direction(direction).build();
                        })
                .toList();
    }
}
