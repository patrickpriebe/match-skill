package com.matchskill.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.matchskill.backend.dto.common.PageResponse;
import com.matchskill.backend.dto.match.MatchResponse;
import com.matchskill.backend.entity.Availability;
import com.matchskill.backend.entity.ExchangeStrength;
import com.matchskill.backend.entity.Skill;
import com.matchskill.backend.entity.SkillDirection;
import com.matchskill.backend.entity.SkillStatus;
import com.matchskill.backend.entity.User;
import com.matchskill.backend.entity.UserSkill;
import com.matchskill.backend.exception.ApiException;
import com.matchskill.backend.repository.AvailabilityRepository;
import com.matchskill.backend.repository.SkillRepository;
import com.matchskill.backend.repository.UserRepository;
import com.matchskill.backend.repository.UserSkillRepository;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    @Mock
    private UserSkillRepository userSkillRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AvailabilityRepository availabilityRepository;

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private ReputationService reputationService;

    @InjectMocks
    private MatchService matchService;

    private User user(UUID id, String name) {
        return User.builder().id(id).displayName(name).timeZone("UTC").build();
    }

    private Skill skill(UUID id, String name) {
        return Skill.builder().id(id).name(name).slug(name.toLowerCase()).status(SkillStatus.APPROVED).build();
    }

    private UserSkill userSkill(User user, Skill skill, SkillDirection direction) {
        return UserSkill.builder().user(user).skill(skill).direction(direction).build();
    }

    @Test
    @DisplayName("Calculates MUTUAL vs PARTIAL and ranks MUTUAL > PARTIAL, then by reputation, then availability")
    void shouldRankMutualBeforePartialThenByReputationThenByAvailability() {
        UUID meId = UUID.randomUUID();
        User me = user(meId, "Me");
        when(userRepository.findById(meId)).thenReturn(Optional.of(me));

        Skill java = skill(UUID.randomUUID(), "Java");
        Skill react = skill(UUID.randomUUID(), "React");

        // Me: offers Java, wants React
        when(userSkillRepository.findApprovedByUserIdAndDirection(meId, SkillDirection.OFFERED))
                .thenReturn(List.of(userSkill(me, java, SkillDirection.OFFERED)));
        when(userSkillRepository.findApprovedByUserIdAndDirection(meId, SkillDirection.WANTED))
                .thenReturn(List.of(userSkill(me, react, SkillDirection.WANTED)));

        // Candidate 1: MUTUAL with 4.0 reputation
        UUID cand1Id = UUID.randomUUID();
        User cand1 = user(cand1Id, "Cand1_Mutual_4.0");

        // Candidate 2: MUTUAL with 4.8 reputation
        UUID cand2Id = UUID.randomUUID();
        User cand2 = user(cand2Id, "Cand2_Mutual_4.8");

        // Candidate 3: PARTIAL with 5.0 reputation (Offers React, wants Python)
        UUID cand3Id = UUID.randomUUID();
        User cand3 = user(cand3Id, "Cand3_Partial_5.0");

        Set<UUID> candidateIds = Set.of(cand1Id, cand2Id, cand3Id);

        // All 3 candidates offer React (which Me wants)
        when(userSkillRepository.findApprovedBySkillIdInAndDirection(Set.of(react.getId()), SkillDirection.OFFERED))
                .thenReturn(List.of(
                        userSkill(cand1, react, SkillDirection.OFFERED),
                        userSkill(cand2, react, SkillDirection.OFFERED),
                        userSkill(cand3, react, SkillDirection.OFFERED)));

        // Classify candidate skills:
        // Cand1 offers React, wants Java (MUTUAL)
        // Cand2 offers React, wants Java (MUTUAL)
        // Cand3 offers React, wants Python (PARTIAL)
        Skill python = skill(UUID.randomUUID(), "Python");
        when(userSkillRepository.findApprovedByUserIdInAndDirection(candidateIds, SkillDirection.OFFERED))
                .thenReturn(List.of(
                        userSkill(cand1, react, SkillDirection.OFFERED),
                        userSkill(cand2, react, SkillDirection.OFFERED),
                        userSkill(cand3, react, SkillDirection.OFFERED)));
        when(userSkillRepository.findApprovedByUserIdInAndDirection(candidateIds, SkillDirection.WANTED))
                .thenReturn(List.of(
                        userSkill(cand1, java, SkillDirection.WANTED),
                        userSkill(cand2, java, SkillDirection.WANTED),
                        userSkill(cand3, python, SkillDirection.WANTED)));

        when(userRepository.findAllById(candidateIds))
                .thenReturn(List.of(cand1, cand2, cand3));

        // No availability recorded for simplicity in this ranking test
        when(availabilityRepository.findByUserId(meId)).thenReturn(List.of());
        when(availabilityRepository.findByUserIdIn(candidateIds)).thenReturn(List.of());

        // Reputations (batched, no N+1):
        when(reputationService.ofBatch(candidateIds)).thenReturn(Map.of(
                cand1Id, new Reputation(4.0, 5),
                cand2Id, new Reputation(4.8, 10),
                cand3Id, new Reputation(5.0, 20)));

        Pageable pageable = PageRequest.of(0, 10);
        PageResponse<MatchResponse> response = matchService.getMatches(meId, pageable);

        List<MatchResponse> items = response.items();
        assertThat(items).hasSize(3);

        // Expected Order:
        // 1st: Cand 2 (MUTUAL, 4.8)
        // 2nd: Cand 1 (MUTUAL, 4.0)
        // 3rd: Cand 3 (PARTIAL, 5.0 - behind all MUTUAL)
        assertThat(items.get(0).userId()).isEqualTo(cand2Id);
        assertThat(items.get(0).strength()).isEqualTo(ExchangeStrength.MUTUAL);

        assertThat(items.get(1).userId()).isEqualTo(cand1Id);
        assertThat(items.get(1).strength()).isEqualTo(ExchangeStrength.MUTUAL);

        assertThat(items.get(2).userId()).isEqualTo(cand3Id);
        assertThat(items.get(2).strength()).isEqualTo(ExchangeStrength.PARTIAL);
    }

    @Test
    @DisplayName("Availability filter: drops match when both recorded availability but have zero overlap")
    void shouldExcludeMatchWhenBothHaveAvailabilityAndZeroOverlap() {
        UUID meId = UUID.randomUUID();
        User me = user(meId, "Me");
        when(userRepository.findById(meId)).thenReturn(Optional.of(me));

        Skill react = skill(UUID.randomUUID(), "React");
        when(userSkillRepository.findApprovedByUserIdAndDirection(meId, SkillDirection.OFFERED)).thenReturn(List.of());
        when(userSkillRepository.findApprovedByUserIdAndDirection(meId, SkillDirection.WANTED))
                .thenReturn(List.of(userSkill(me, react, SkillDirection.WANTED)));

        UUID candId = UUID.randomUUID();
        User cand = user(candId, "Cand");

        when(userSkillRepository.findApprovedBySkillIdInAndDirection(Set.of(react.getId()), SkillDirection.OFFERED))
                .thenReturn(List.of(userSkill(cand, react, SkillDirection.OFFERED)));

        when(userSkillRepository.findApprovedByUserIdInAndDirection(Set.of(candId), SkillDirection.OFFERED))
                .thenReturn(List.of(userSkill(cand, react, SkillDirection.OFFERED)));
        when(userSkillRepository.findApprovedByUserIdInAndDirection(Set.of(candId), SkillDirection.WANTED))
                .thenReturn(List.of());

        when(userRepository.findAllById(Set.of(candId))).thenReturn(List.of(cand));

        // Me available on Monday 09:00 - 10:00
        Availability meAvail = Availability.builder()
                .dayOfWeek(DayOfWeek.MONDAY)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .build();
        when(availabilityRepository.findByUserId(meId)).thenReturn(List.of(meAvail));

        // Candidate available on Tuesday 09:00 - 10:00 (zero overlap)
        Availability candAvail = Availability.builder()
                .user(cand)
                .dayOfWeek(DayOfWeek.TUESDAY)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .build();
        when(availabilityRepository.findByUserIdIn(Set.of(candId))).thenReturn(List.of(candAvail));
        when(reputationService.ofBatch(Set.of(candId))).thenReturn(Map.of(candId, Reputation.NONE));

        PageResponse<MatchResponse> response = matchService.getMatches(meId, PageRequest.of(0, 10));

        assertThat(response.items()).isEmpty();
    }

    @Test
    @DisplayName("Self-match prevention: current user is filtered out of candidate list")
    void shouldFilterOutCurrentUserFromMatches() {
        UUID meId = UUID.randomUUID();
        User me = user(meId, "Me");
        when(userRepository.findById(meId)).thenReturn(Optional.of(me));

        Skill java = skill(UUID.randomUUID(), "Java");
        when(userSkillRepository.findApprovedByUserIdAndDirection(meId, SkillDirection.OFFERED)).thenReturn(List.of());
        when(userSkillRepository.findApprovedByUserIdAndDirection(meId, SkillDirection.WANTED))
                .thenReturn(List.of(userSkill(me, java, SkillDirection.WANTED)));

        // Returns myself offering the skill I want
        when(userSkillRepository.findApprovedBySkillIdInAndDirection(Set.of(java.getId()), SkillDirection.OFFERED))
                .thenReturn(List.of(userSkill(me, java, SkillDirection.OFFERED)));

        PageResponse<MatchResponse> response = matchService.getMatches(meId, PageRequest.of(0, 10));

        assertThat(response.items()).isEmpty();
        verifyNoInteractions(availabilityRepository, reputationService);
    }

    @Test
    @DisplayName("Search: queries users offering specific skill")
    void shouldSearchUsersOfferingSpecificSkill() {
        UUID viewerId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        Skill skill = skill(skillId, "Rust");

        when(userRepository.findById(viewerId)).thenReturn(Optional.of(user(viewerId, "Viewer")));
        when(skillRepository.findById(skillId)).thenReturn(Optional.of(skill));
        when(userSkillRepository.findApprovedByUserIdAndDirection(viewerId, SkillDirection.OFFERED)).thenReturn(List.of());
        when(userSkillRepository.findApprovedByUserIdAndDirection(viewerId, SkillDirection.WANTED)).thenReturn(List.of());

        UUID candId = UUID.randomUUID();
        User cand = user(candId, "Ferris");

        when(userSkillRepository.findApprovedBySkillIdInAndDirection(Set.of(skillId), SkillDirection.OFFERED))
                .thenReturn(List.of(userSkill(cand, skill, SkillDirection.OFFERED)));
        when(userSkillRepository.findApprovedByUserIdInAndDirection(Set.of(candId), SkillDirection.OFFERED))
                .thenReturn(List.of(userSkill(cand, skill, SkillDirection.OFFERED)));
        when(userSkillRepository.findApprovedByUserIdInAndDirection(Set.of(candId), SkillDirection.WANTED))
                .thenReturn(List.of());
        when(userRepository.findAllById(Set.of(candId))).thenReturn(List.of(cand));
        when(reputationService.ofBatch(Set.of(candId))).thenReturn(Map.of(candId, new Reputation(4.5, 2)));

        PageResponse<MatchResponse> response = matchService.search(viewerId, skillId, PageRequest.of(0, 10));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).displayName()).isEqualTo("Ferris");
        assertThat(response.items().get(0).strength()).isEqualTo(ExchangeStrength.PARTIAL);
    }

    @Test
    @DisplayName("Search: throws 404 when skill not found")
    void shouldThrowNotFoundWhenSkillDoesNotExist() {
        UUID viewerId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        when(skillRepository.findById(skillId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchService.search(viewerId, skillId, PageRequest.of(0, 10)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getCode()).isEqualTo("SKILL_NOT_FOUND");
                });
    }

    @Test
    void shouldRejectSearchForPendingSkill() {
        UUID skillId = UUID.randomUUID();
        Skill pending = skill(skillId, "Pending");
        pending.setStatus(SkillStatus.PENDING_REVIEW);
        when(skillRepository.findById(skillId)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> matchService.search(UUID.randomUUID(), skillId, PageRequest.of(0, 10)))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("SKILL_NOT_FOUND"));
        verifyNoInteractions(userSkillRepository, availabilityRepository, reputationService);
    }

    @Test
    void shouldSkipCandidateQueriesWhenThereAreNoApprovedWantedSkills() {
        UUID viewerId = UUID.randomUUID();
        when(userRepository.findById(viewerId)).thenReturn(Optional.of(user(viewerId, "Viewer")));

        PageResponse<MatchResponse> response = matchService.getMatches(viewerId, PageRequest.of(0, 10));

        assertThat(response.items()).isEmpty();
        assertThat(response.total()).isZero();
        verify(userSkillRepository, never()).findApprovedBySkillIdInAndDirection(any(), any());
        verifyNoInteractions(availabilityRepository, reputationService);
    }

    @Test
    void shouldRankSearchByCountThenOverlapThenStableIdAndRetainNonoverlappingCandidate() {
        UUID viewerId = UUID.randomUUID();
        User viewer = user(viewerId, "Viewer");
        Skill react = skill(UUID.randomUUID(), "React");
        User stableFirst = user(new UUID(0, 1), "Stable first");
        User stableSecond = user(new UUID(0, 2), "Stable second");
        User longOverlap = user(new UUID(0, 3), "Long overlap");
        User moreRatings = user(new UUID(0, 4), "More ratings, no overlap");
        List<User> candidates = List.of(stableSecond, longOverlap, stableFirst, moreRatings);
        Set<UUID> candidateIds = candidates.stream().map(User::getId).collect(java.util.stream.Collectors.toSet());
        List<UserSkill> offerings = candidates.stream()
                .map(candidate -> userSkill(candidate, react, SkillDirection.OFFERED)).toList();
        when(userRepository.findById(viewerId)).thenReturn(Optional.of(viewer));
        when(skillRepository.findById(react.getId())).thenReturn(Optional.of(react));
        when(userSkillRepository.findApprovedBySkillIdInAndDirection(Set.of(react.getId()), SkillDirection.OFFERED))
                .thenReturn(offerings);
        when(userSkillRepository.findApprovedByUserIdInAndDirection(candidateIds, SkillDirection.OFFERED))
                .thenReturn(offerings);
        when(userRepository.findAllById(candidateIds)).thenReturn(candidates);
        when(reputationService.ofBatch(candidateIds)).thenReturn(Map.of(
                stableFirst.getId(), new Reputation(4, 2),
                stableSecond.getId(), new Reputation(4, 2),
                longOverlap.getId(), new Reputation(4, 2),
                moreRatings.getId(), new Reputation(4, 3)));
        when(availabilityRepository.findByUserId(viewerId))
                .thenReturn(List.of(window(viewer, DayOfWeek.MONDAY, 9, 12)));
        when(availabilityRepository.findByUserIdIn(candidateIds)).thenReturn(List.of(
                window(stableFirst, DayOfWeek.MONDAY, 9, 10),
                window(stableSecond, DayOfWeek.MONDAY, 9, 10),
                window(longOverlap, DayOfWeek.MONDAY, 9, 11),
                window(moreRatings, DayOfWeek.TUESDAY, 9, 12)));

        PageResponse<MatchResponse> response = matchService.search(viewerId, react.getId(), PageRequest.of(0, 10));

        assertThat(response.items()).extracting(MatchResponse::userId)
                .containsExactly(moreRatings.getId(), longOverlap.getId(), stableFirst.getId(), stableSecond.getId());
        assertThat(response.items()).extracting(MatchResponse::strength).containsOnly(ExchangeStrength.PARTIAL);

        PageResponse<MatchResponse> distant =
                matchService.search(viewerId, react.getId(), PageRequest.of(Integer.MAX_VALUE, 100));
        assertThat(distant.items()).isEmpty();
        assertThat(distant.total()).isEqualTo(4);
        assertThat(distant.page()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void shouldSkipSearchCandidatesWhoseApprovedSkillsNoLongerMatch() {
        UUID viewerId = UUID.randomUUID();
        Skill react = skill(UUID.randomUUID(), "React");
        User candidate = user(UUID.randomUUID(), "Candidate");
        when(userRepository.findById(viewerId)).thenReturn(Optional.of(user(viewerId, "Viewer")));
        when(skillRepository.findById(react.getId())).thenReturn(Optional.of(react));
        when(userSkillRepository.findApprovedBySkillIdInAndDirection(Set.of(react.getId()), SkillDirection.OFFERED))
                .thenReturn(List.of(userSkill(candidate, react, SkillDirection.OFFERED)));
        when(userRepository.findAllById(Set.of(candidate.getId()))).thenReturn(List.of(candidate));

        PageResponse<MatchResponse> response = matchService.search(viewerId, react.getId(), PageRequest.of(0, 10));

        assertThat(response.items()).isEmpty();
    }

    private Availability window(User user, DayOfWeek day, int startHour, int endHour) {
        return Availability.builder().user(user).dayOfWeek(day)
                .startTime(LocalTime.of(startHour, 0)).endTime(LocalTime.of(endHour, 0)).build();
    }
}
