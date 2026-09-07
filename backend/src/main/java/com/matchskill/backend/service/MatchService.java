package com.matchskill.backend.service;

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
import com.matchskill.backend.util.AvailabilityOverlap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

    private record Candidate(User user, ExchangeStrength strength, Reputation reputation, int overlapMinutes) {}

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

        Map<UUID, ExchangeStrength> strengths = classify(candidateIds, myOffered, myWanted);
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
            int overlapMinutes =
                    overlapMinutes(me, myAvailability, candidateUser, theirAvailability);
            // Two complementary skill lists that never overlap in time are not a usable
            // match — but only once both sides actually recorded availability.
            if (bothHaveAvailability && overlapMinutes == 0) {
                continue;
            }
            candidates.add(
                    new Candidate(
                            candidateUser, strength, reputations.getOrDefault(candidateId, Reputation.NONE), overlapMinutes));
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
        Set<UUID> searchWanted = new java.util.HashSet<>(myWanted);
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

        Map<UUID, ExchangeStrength> strengths = classify(candidateIds, myOffered, searchWanted);
        Map<UUID, User> users = usersById(candidateIds);
        Map<UUID, Reputation> reputations = reputationService.ofBatch(candidateIds);
        List<Availability> viewerAvailability = availabilityRepository.findByUserId(viewerId);
        Map<UUID, List<Availability>> availabilityByUser = availabilityByUser(candidateIds);

        List<Candidate> candidates =
                candidateIds.stream()
                        .filter(users::containsKey)
                        .filter(strengths::containsKey)
                        .map(
                                id ->
                                        new Candidate(
                                                users.get(id),
                                                strengths.get(id),
                                                reputations.getOrDefault(id, Reputation.NONE),
                                                overlapMinutes(
                                                        viewer,
                                                        viewerAvailability,
                                                        users.get(id),
                                                        availabilityByUser.getOrDefault(id, List.of()))))
                        .toList();

        List<Candidate> sorted = new ArrayList<>(candidates);
        sorted.sort(rankingComparator());
        return paginate(sorted, pageable, MatchService::toResponse);
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
     * otherwise. Batches the candidates' own offered/wanted sets in two queries.
     */
    private Map<UUID, ExchangeStrength> classify(Set<UUID> candidateIds, Set<UUID> myOffered, Set<UUID> myWanted) {
        if (candidateIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Set<UUID>> candidateOffered =
                groupSkillIdsByUser(
                        userSkillRepository.findApprovedByUserIdInAndDirection(candidateIds, SkillDirection.OFFERED));
        Map<UUID, Set<UUID>> candidateWanted =
                groupSkillIdsByUser(
                        userSkillRepository.findApprovedByUserIdInAndDirection(candidateIds, SkillDirection.WANTED));

        Map<UUID, ExchangeStrength> result = new java.util.HashMap<>();
        for (UUID id : candidateIds) {
            boolean theyOfferSomethingIWant =
                    !Collections.disjoint(candidateOffered.getOrDefault(id, Set.of()), myWanted);
            if (theyOfferSomethingIWant) {
                boolean iOfferSomethingTheyWant =
                        !Collections.disjoint(myOffered, candidateWanted.getOrDefault(id, Set.of()));
                result.put(id, iOfferSomethingTheyWant ? ExchangeStrength.MUTUAL : ExchangeStrength.PARTIAL);
            }
        }
        return result;
    }

    private Map<UUID, Set<UUID>> groupSkillIdsByUser(List<UserSkill> entries) {
        return entries.stream()
                .collect(
                        Collectors.groupingBy(
                                us -> us.getUser().getId(),
                                Collectors.mapping(us -> us.getSkill().getId(), Collectors.toSet())));
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

    private int overlapMinutes(User viewer, List<Availability> viewerAvailability,
            User candidate, List<Availability> candidateAvailability) {
        if (viewerAvailability.isEmpty() || candidateAvailability.isEmpty()) {
            return 0;
        }
        return AvailabilityOverlap.overlapMinutes(
                viewerAvailability, viewer.getTimeZone(), candidateAvailability, candidate.getTimeZone());
    }

    private static MatchResponse toResponse(Candidate candidate) {
        return new MatchResponse(
                candidate.user().getId(),
                candidate.user().getDisplayName(),
                candidate.user().getBio(),
                candidate.strength(),
                candidate.reputation().average(),
                candidate.reputation().count());
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
