package com.matchskill.backend.service;

import com.matchskill.backend.dto.ring.RingMemberResponse;
import com.matchskill.backend.dto.ring.RingResponse;
import com.matchskill.backend.dto.skill.SkillResponse;
import com.matchskill.backend.entity.Availability;
import com.matchskill.backend.entity.Skill;
import com.matchskill.backend.entity.SkillDirection;
import com.matchskill.backend.entity.User;
import com.matchskill.backend.entity.UserSkill;
import com.matchskill.backend.exception.ApiException;
import com.matchskill.backend.repository.AvailabilityRepository;
import com.matchskill.backend.repository.UserRepository;
import com.matchskill.backend.repository.UserSkillRepository;
import com.matchskill.backend.util.AvailabilityOverlap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds three-person trade rings: A teaches B, B teaches C, C teaches A.
 *
 * <p>Direct barter needs a double coincidence of wants — I have to want
 * exactly what you can teach <em>and</em> teach exactly what you want. That is
 * rare, and it is why most pairs on this platform end up as PARTIAL, which is
 * a polite way of saying "ask them for a favour". A ring needs no such
 * coincidence: everybody teaches one person and learns from another, and
 * nobody has to trade with the person they are teaching.
 *
 * <p>Rings of three only, deliberately. Three is where the idea lives — it is
 * the smallest cycle that no pairwise matcher can find. Four multiplies the
 * search frontier by the neighbourhood of every intermediate member while
 * adding nothing conceptually, and in a graph this size a four-ring is
 * vanishingly rare. The natural extension is a bounded depth-first walk over
 * the same edges, and it belongs behind a measurement, not a guess.
 */
@Service
public class RingService {

    /**
     * Ceiling on each side of the search. The work is one set intersection per
     * (student, teacher) pair, so the cost is the product: a cap keeps a
     * popular skill from turning the home feed into a quadratic scan.
     */
    private static final int MAX_CANDIDATES_PER_SIDE = 300;

    private final UserSkillRepository userSkillRepository;
    private final UserRepository userRepository;
    private final AvailabilityRepository availabilityRepository;
    private final ReputationService reputationService;

    public RingService(
            UserSkillRepository userSkillRepository,
            UserRepository userRepository,
            AvailabilityRepository availabilityRepository,
            ReputationService reputationService) {
        this.userSkillRepository = userSkillRepository;
        this.userRepository = userRepository;
        this.availabilityRepository = availabilityRepository;
        this.reputationService = reputationService;
    }

    /** Rings that include this user, best first. Never more than {@code limit}. */
    @Transactional(readOnly = true)
    public List<RingResponse> findRings(UUID viewerId, int limit) {
        User viewer =
                userRepository
                        .findById(viewerId)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

        List<Skill> myOffered = skills(viewerId, SkillDirection.OFFERED);
        List<Skill> myWanted = skills(viewerId, SkillDirection.WANTED);
        if (myOffered.isEmpty() || myWanted.isEmpty()) {
            // A ring needs this user to both teach and learn. One empty list is
            // not a failed search, it is an unanswerable question.
            return List.of();
        }

        // Who could learn from me, and who could teach me. A ring through this
        // user is a bridge from one set to the other.
        Set<UUID> students = usersWith(ids(myOffered), SkillDirection.WANTED, viewerId);
        Set<UUID> teachers = usersWith(ids(myWanted), SkillDirection.OFFERED, viewerId);
        if (students.isEmpty() || teachers.isEmpty()) {
            return List.of();
        }

        Set<UUID> everyone = new HashSet<>(students);
        everyone.addAll(teachers);
        Map<UUID, List<Skill>> offered = skillsByUser(everyone, SkillDirection.OFFERED);
        Map<UUID, List<Skill>> wanted = skillsByUser(everyone, SkillDirection.WANTED);
        Map<UUID, User> users =
                userRepository.findAllById(everyone).stream().collect(Collectors.toMap(User::getId, u -> u));
        Map<UUID, Reputation> reputations = reputationService.ofBatch(everyone);
        Map<UUID, List<Availability>> availability = availabilityByUser(everyone);
        List<Availability> myAvailability = availabilityRepository.findByUserId(viewerId);

        List<RingResponse> rings = new ArrayList<>();
        for (UUID studentId : students) {
            User student = users.get(studentId);
            if (student == null) {
                continue;
            }
            Optional<Skill> iTeachStudent = pick(myOffered, wanted.get(studentId));
            if (iTeachStudent.isEmpty()) {
                continue;
            }
            int myPairMinutes =
                    minutes(myAvailability, viewer, availability.get(studentId), student);
            if (blocked(myAvailability, availability.get(studentId), myPairMinutes)) {
                continue;
            }

            for (UUID teacherId : teachers) {
                if (teacherId.equals(studentId)) {
                    // The same person on both sides is a plain mutual match, and
                    // the feed already shows it as one. A ring that collapses
                    // into a pair is not a third option, it is a duplicate.
                    continue;
                }
                User teacher = users.get(teacherId);
                if (teacher == null) {
                    continue;
                }
                Optional<Skill> studentTeachesTeacher = pick(offered.get(studentId), wanted.get(teacherId));
                Optional<Skill> teacherTeachesMe = pick(offered.get(teacherId), myWanted);
                if (studentTeachesTeacher.isEmpty() || teacherTeachesMe.isEmpty()) {
                    continue;
                }

                int middleMinutes =
                        minutes(availability.get(studentId), student, availability.get(teacherId), teacher);
                if (blocked(availability.get(studentId), availability.get(teacherId), middleMinutes)) {
                    continue;
                }
                int closingMinutes =
                        minutes(availability.get(teacherId), teacher, myAvailability, viewer);
                if (blocked(availability.get(teacherId), myAvailability, closingMinutes)) {
                    continue;
                }

                rings.add(
                        new RingResponse(
                                List.of(
                                        member(viewer, reputations, iTeachStudent.get(), teacherTeachesMe.get()),
                                        member(student, reputations, studentTeachesTeacher.get(), iTeachStudent.get()),
                                        member(teacher, reputations, teacherTeachesMe.get(), studentTeachesTeacher.get())),
                                Math.min(myPairMinutes, Math.min(middleMinutes, closingMinutes))));
            }
        }

        rings.sort(ranking());
        return rings.size() <= limit ? List.copyOf(rings) : List.copyOf(rings.subList(0, limit));
    }

    /**
     * The weakest link first: a ring is only as schedulable as its worst pair.
     * Reputation breaks the tie, and the member ids settle the rest so the same
     * request does not reshuffle between calls.
     */
    private Comparator<RingResponse> ranking() {
        return Comparator.comparingInt(RingResponse::weakestLinkMinutes)
                .reversed()
                .thenComparing(Comparator.comparingDouble(RingService::averageRating).reversed())
                .thenComparing(ring -> ring.members().stream().map(m -> m.userId().toString()).toList().toString());
    }

    private static double averageRating(RingResponse ring) {
        return ring.members().stream()
                .skip(1) // The asking user's own rating says nothing about the ring.
                .mapToDouble(RingMemberResponse::reputationAverage)
                .average()
                .orElse(0);
    }

    private RingMemberResponse member(
            User user, Map<UUID, Reputation> reputations, Skill teaches, Skill learns) {
        Reputation reputation = reputations.getOrDefault(user.getId(), Reputation.NONE);
        return new RingMemberResponse(
                user.getId(),
                user.getDisplayName(),
                user.getTimeZone(),
                reputation.average(),
                reputation.count(),
                SkillResponse.from(teaches),
                SkillResponse.from(learns));
    }

    /**
     * The skill that flows along one edge. Deterministic by name so the same
     * ring is described the same way on every request — a card that renames
     * its own trade between reloads reads as a bug.
     */
    private Optional<Skill> pick(List<Skill> teacherOffers, List<Skill> learnerWants) {
        if (teacherOffers == null || learnerWants == null) {
            return Optional.empty();
        }
        Set<UUID> wantedIds = ids(learnerWants);
        return teacherOffers.stream()
                .filter(skill -> wantedIds.contains(skill.getId()))
                .min(Comparator.comparing(Skill::getName).thenComparing(Skill::getId));
    }

    /**
     * An edge is impossible only when both sides published a week and those
     * weeks never meet. Someone who has not filled in their hours is unknown,
     * not unavailable, and excluding them would punish the newest accounts.
     */
    private boolean blocked(List<Availability> a, List<Availability> b, int overlapMinutes) {
        boolean bothPublished = a != null && !a.isEmpty() && b != null && !b.isEmpty();
        return bothPublished && overlapMinutes == 0;
    }

    private int minutes(List<Availability> a, User userA, List<Availability> b, User userB) {
        if (a == null || a.isEmpty() || b == null || b.isEmpty()) {
            return 0;
        }
        return AvailabilityOverlap.overlapMinutes(a, userA.getTimeZone(), b, userB.getTimeZone());
    }

    private List<Skill> skills(UUID userId, SkillDirection direction) {
        return userSkillRepository.findApprovedByUserIdAndDirection(userId, direction).stream()
                .map(UserSkill::getSkill)
                .toList();
    }

    private Set<UUID> usersWith(Set<UUID> skillIds, SkillDirection direction, UUID excluded) {
        return userSkillRepository.findApprovedBySkillIdInAndDirection(skillIds, direction).stream()
                .map(us -> us.getUser().getId())
                .filter(id -> !id.equals(excluded))
                .distinct()
                .limit(MAX_CANDIDATES_PER_SIDE)
                .collect(Collectors.toSet());
    }

    private Map<UUID, List<Skill>> skillsByUser(Set<UUID> userIds, SkillDirection direction) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userSkillRepository.findApprovedByUserIdInAndDirection(userIds, direction).stream()
                .collect(
                        Collectors.groupingBy(
                                us -> us.getUser().getId(),
                                Collectors.mapping(UserSkill::getSkill, Collectors.toList())));
    }

    private Map<UUID, List<Availability>> availabilityByUser(Set<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return availabilityRepository.findByUserIdIn(userIds).stream()
                .collect(Collectors.groupingBy(a -> a.getUser().getId()));
    }

    private static Set<UUID> ids(List<Skill> skills) {
        return skills.stream().map(Skill::getId).collect(Collectors.toSet());
    }
}
