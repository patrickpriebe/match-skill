package com.matchskill.backend.service;

import com.matchskill.backend.dto.common.PageResponse;
import com.matchskill.backend.dto.match.MatchResponse;
import com.matchskill.backend.dto.match.SharedWindowResponse;
import com.matchskill.backend.dto.skill.SkillResponse;
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
import com.matchskill.backend.util.AvailabilityOverlap;
import com.matchskill.backend.util.AvailabilityOverlap.SharedWindow;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the ranked candidate lists for the home feed and the deliberate
 * search page. Candidates come from a handful of batched queries; ranking
 * itself (strength, rating average and count, then availability overlap) runs in
 * memory, since it combines several independent signals no single query
 * expresses cleanly at this scale. Pagination (see {@link #paginate}) is
 * therefore also in-memory: the full candidate set for a match/search skill
 * is loaded and sorted before slicing a page, which is fine while a single
 * skill's candidate pool stays in the hundreds, but would need a DB-level
 * ranking query if that pool grows into the tens of thousands.
 *
 * <p>The response carries the same skill lists and shared windows the ranking
 * used. That is deliberate: the browser once re-fetched a full profile per row
 * to render a card, and the overlap that decides the order was computed here
 * and discarded.
 */
@Service
public class MatchService {

    private final UserSkillRepository userSkillRepository;
    private final UserRepository userRepository;
    private final AvailabilityRepository availabilityRepository;
    private final SkillRepository skillRepository;
    private final ReputationService reputationService;

    public MatchService(
            UserSkillRepository userSkillRepository,
            UserRepository userRepository,
            AvailabilityRepository availabilityRepository,
            SkillRepository skillRepository,
            ReputationService reputationService) {
        this.userSkillRepository = userSkillRepository;
        this.userRepository = userRepository;
        this.availabilityRepository = availabilityRepository;
        this.skillRepository = skillRepository;
        this.reputationService = reputationService;
    }

    private record Candidate(
            User user,
            ExchangeStrength strength,
            Reputation reputation,
            int overlapMinutes,
            List<SharedWindow> sharedWindows,
            List<Skill> offered,
            List<Skill> wanted) {}

    /** Home feed: only users who offer a skill this user wants, MUTUAL first, availability-filtered. */
    @Transactional(readOnly = true)
    public PageResponse<MatchResponse> getMatches(UUID userId, Pageable pageable) {
        User me = requireUser(userId);
        Set<UUID> myOffered = skillIds(userId, SkillDirection.OFFERED);
        Set<UUID> myWanted = skillIds(userId, SkillDirection.WANTED);
        if (myWanted.isEmpty()) {
            return paginate(List.of(), pageable, MatchService::toResponse);
        }

        Set<UUID> candidateIds =
                userSkillRepository.findApprovedBySkillIdInAndDirection(myWanted, SkillDirection.OFFERED).stream()
                        .map(us -> us.getUser().getId())
                        .filter(id -> !id.equals(userId))
                        .collect(Collectors.toSet());
        if (candidateIds.isEmpty()) {
            return paginate(List.of(), pageable, MatchService::toResponse);
        }

        Map<UUID, List<Skill>> candidateOffered = skillsByUser(candidateIds, SkillDirection.OFFERED);
        Map<UUID, List<Skill>> candidateWanted = skillsByUser(candidateIds, SkillDirection.WANTED);
        Map<UUID, ExchangeStrength> strengths =
                classify(candidateIds, myOffered, myWanted, candidateOffered, candidateWanted);
        List<Availability> myAvailability = availabilityRepository.findByUserId(userId);
        Map<UUID, User> users = usersById(candidateIds);
        Map<UUID, List<Availability>> availabilityByUser = availabilityByUser(candidateIds);
        Map<UUID, Reputation> reputations = reputationService.ofBatch(candidateIds);

        List<Candidate> candidates = new ArrayList<>();
        for (UUID candidateId : candidateIds) {
            User candidateUser = users.get(candidateId);
            ExchangeStrength strength = strengths.get(candidateId);
            if (candidateUser == null || strength == null) {
                continue;
            }
            List<Availability> theirAvailability = availabilityByUser.getOrDefault(candidateId, List.of());
            boolean bothHaveAvailability = !myAvailability.isEmpty() && !theirAvailability.isEmpty();
            List<SharedWindow> shared =
                    sharedWindows(me, myAvailability, candidateUser, theirAvailability);
            int overlapMinutes = totalMinutes(shared);
            // Two complementary skill lists that never overlap in time are not a usable
            // match — but only once both sides actually recorded availability.
            if (bothHaveAvailability && overlapMinutes == 0) {
                continue;
            }
            candidates.add(
                    new Candidate(
                            candidateUser,
                            strength,
                            reputations.getOrDefault(candidateId, Reputation.NONE),
                            overlapMinutes,
                            shared,
                            candidateOffered.getOrDefault(candidateId, List.of()),
                            candidateWanted.getOrDefault(candidateId, List.of())));
        }

        candidates.sort(rankingComparator());
        return paginate(candidates, pageable, MatchService::toResponse);
    }

    /** Deliberate lookup: every user offering the given skill, ranked the same way as the home feed. */
    @Transactional(readOnly = true)
    public PageResponse<MatchResponse> search(UUID viewerId, UUID skillId, Pageable pageable) {
        Skill skill =
                skillRepository
                        .findById(skillId)
                        .filter(value -> value.getStatus() == SkillStatus.APPROVED)
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "SKILL_NOT_FOUND", "Skill not found"));

        User viewer = requireUser(viewerId);
        Set<UUID> myOffered = skillIds(viewerId, SkillDirection.OFFERED);
        Set<UUID> myWanted = skillIds(viewerId, SkillDirection.WANTED);
        Set<UUID> searchWanted = new HashSet<>(myWanted);
        searchWanted.add(skill.getId());

        Set<UUID> candidateIds =
                userSkillRepository
                        .findApprovedBySkillIdInAndDirection(Set.of(skill.getId()), SkillDirection.OFFERED)
                        .stream()
                        .map(us -> us.getUser().getId())
                        .filter(id -> !id.equals(viewerId))
                        .collect(Collectors.toSet());
        if (candidateIds.isEmpty()) {
            return paginate(List.of(), pageable, MatchService::toResponse);
        }

        Map<UUID, List<Skill>> candidateOffered = skillsByUser(candidateIds, SkillDirection.OFFERED);
        Map<UUID, List<Skill>> candidateWanted = skillsByUser(candidateIds, SkillDirection.WANTED);
        Map<UUID, ExchangeStrength> strengths =
                classify(candidateIds, myOffered, searchWanted, candidateOffered, candidateWanted);
        Map<UUID, User> users = usersById(candidateIds);
        Map<UUID, Reputation> reputations = reputationService.ofBatch(candidateIds);
        List<Availability> viewerAvailability = availabilityRepository.findByUserId(viewerId);
        Map<UUID, List<Availability>> availabilityByUser = availabilityByUser(candidateIds);

        List<Candidate> candidates = new ArrayList<>();
        for (UUID candidateId : candidateIds) {
            User candidateUser = users.get(candidateId);
            ExchangeStrength strength = strengths.get(candidateId);
            if (candidateUser == null || strength == null) {
                continue;
            }
            List<SharedWindow> shared =
                    sharedWindows(
                            viewer,
                            viewerAvailability,
                            candidateUser,
                            availabilityByUser.getOrDefault(candidateId, List.of()));
            candidates.add(
                    new Candidate(
                            candidateUser,
                            strength,
                            reputations.getOrDefault(candidateId, Reputation.NONE),
                            totalMinutes(shared),
                            shared,
                            candidateOffered.getOrDefault(candidateId, List.of()),
                            candidateWanted.getOrDefault(candidateId, List.of())));
        }

        candidates.sort(rankingComparator());
        return paginate(candidates, pageable, MatchService::toResponse);
    }

    private User requireUser(UUID userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
    }

    private Set<UUID> skillIds(UUID userId, SkillDirection direction) {
        return userSkillRepository.findApprovedByUserIdAndDirection(userId, direction).stream()
                .map(us -> us.getSkill().getId())
                .collect(Collectors.toSet());
    }

    /**
     * MUTUAL when the candidate offers something this user wants AND this user offers
     * something the candidate wants; PARTIAL when only the first holds; absent (null)
     * otherwise. Works from the skill lists the caller already loaded, which are the
     * same ones the response carries.
     */
    private Map<UUID, ExchangeStrength> classify(
            Set<UUID> candidateIds,
            Set<UUID> myOffered,
            Set<UUID> myWanted,
            Map<UUID, List<Skill>> candidateOffered,
            Map<UUID, List<Skill>> candidateWanted) {
        Map<UUID, ExchangeStrength> result = new java.util.HashMap<>();
        for (UUID id : candidateIds) {
            boolean theyOfferSomethingIWant = !Collections.disjoint(ids(candidateOffered.get(id)), myWanted);
            if (theyOfferSomethingIWant) {
                boolean iOfferSomethingTheyWant = !Collections.disjoint(myOffered, ids(candidateWanted.get(id)));
                result.put(id, iOfferSomethingTheyWant ? ExchangeStrength.MUTUAL : ExchangeStrength.PARTIAL);
            }
        }
        return result;
    }

    private static Set<UUID> ids(List<Skill> skills) {
        return skills == null ? Set.of() : skills.stream().map(Skill::getId).collect(Collectors.toSet());
    }

    private Map<UUID, List<Skill>> skillsByUser(Set<UUID> candidateIds, SkillDirection direction) {
        if (candidateIds.isEmpty()) {
            return Map.of();
        }
        return userSkillRepository.findApprovedByUserIdInAndDirection(candidateIds, direction).stream()
                .collect(
                        Collectors.groupingBy(
                                us -> us.getUser().getId(),
                                Collectors.mapping(UserSkill::getSkill, Collectors.toList())));
    }

    private Map<UUID, User> usersById(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getId, u -> u));
    }

    private Map<UUID, List<Availability>> availabilityByUser(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return availabilityRepository.findByUserIdIn(ids).stream()
                .collect(Collectors.groupingBy(a -> a.getUser().getId()));
    }

    private Comparator<Candidate> rankingComparator() {
        return Comparator.<Candidate>comparingInt(c -> c.strength() == ExchangeStrength.MUTUAL ? 0 : 1)
                .thenComparing(Comparator.comparingDouble((Candidate c) -> c.reputation().average()).reversed())
                .thenComparing(Comparator.comparingLong((Candidate c) -> c.reputation().count()).reversed())
                .thenComparing(Comparator.comparingInt(Candidate::overlapMinutes).reversed())
                .thenComparing(c -> c.user().getId());
    }

    private List<SharedWindow> sharedWindows(
            User viewer,
            List<Availability> viewerAvailability,
            User candidate,
            List<Availability> candidateAvailability) {
        if (viewerAvailability.isEmpty() || candidateAvailability.isEmpty()) {
            return List.of();
        }
        return AvailabilityOverlap.sharedWindows(
                viewerAvailability, viewer.getTimeZone(), candidateAvailability, candidate.getTimeZone());
    }

    private static int totalMinutes(List<SharedWindow> windows) {
        return windows.stream().mapToInt(SharedWindow::minutes).sum();
    }

    private static MatchResponse toResponse(Candidate candidate) {
        return new MatchResponse(
                candidate.user().getId(),
                candidate.user().getDisplayName(),
                candidate.user().getBio(),
                candidate.user().getTimeZone(),
                candidate.strength(),
                candidate.reputation().average(),
                candidate.reputation().count(),
                candidate.offered().stream().map(SkillResponse::from).toList(),
                candidate.wanted().stream().map(SkillResponse::from).toList(),
                candidate.overlapMinutes(),
                candidate.sharedWindows().stream().map(SharedWindowResponse::from).toList());
    }

    private <R> PageResponse<R> paginate(
            List<Candidate> sorted, Pageable pageable, Function<Candidate, R> mapper) {
        int total = sorted.size();
        int from = (int) Math.min(pageable.getOffset(), total);
        int to = (int) Math.min((long) from + pageable.getPageSize(), total);
        List<R> page = sorted.subList(from, to).stream().map(mapper).toList();
        return new PageResponse<>(page, pageable.getPageNumber(), pageable.getPageSize(), total);
    }
}
