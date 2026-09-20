package com.matchskill.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.lenient;

import com.matchskill.backend.dto.ring.RingResponse;
import com.matchskill.backend.entity.Availability;
import com.matchskill.backend.entity.Skill;
import com.matchskill.backend.entity.SkillDirection;
import com.matchskill.backend.entity.SkillStatus;
import com.matchskill.backend.entity.User;
import com.matchskill.backend.entity.UserSkill;
import com.matchskill.backend.repository.AvailabilityRepository;
import com.matchskill.backend.repository.UserRepository;
import com.matchskill.backend.repository.UserSkillRepository;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The ring is the one thing here a pairwise matcher cannot find, so these
 * tests are mostly about what must <em>not</em> come back: a pair wearing a
 * ring's clothes, and a chain whose weakest link cannot meet.
 */
@ExtendWith(MockitoExtension.class)
class RingServiceTest {

    @Mock
    private UserSkillRepository userSkillRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AvailabilityRepository availabilityRepository;

    @Mock
    private ReputationService reputationService;

    @InjectMocks
    private RingService ringService;

    private final Skill java = skill("Java");
    private final Skill react = skill("React");
    private final Skill python = skill("Python");

    private final User me = user("Me");
    private final User student = user("Student");
    private final User teacher = user("Teacher");

    /** userId -> direction -> skills, assembled into the repository's answers. */
    private final Map<UUID, Map<SkillDirection, List<Skill>>> graph = new HashMap<>();
    private final Map<UUID, List<Availability>> weeks = new HashMap<>();
    private final Map<UUID, User> people = new HashMap<>();

    private static Skill skill(String name) {
        return Skill.builder()
                .id(UUID.randomUUID())
                .name(name)
                .slug(name.toLowerCase())
                .status(SkillStatus.APPROVED)
                .build();
    }

    private static User user(String name) {
        return User.builder().id(UUID.randomUUID()).displayName(name).timeZone("UTC").build();
    }

    private void declare(User user, List<Skill> offered, List<Skill> wanted) {
        people.put(user.getId(), user);
        graph.put(user.getId(), Map.of(SkillDirection.OFFERED, offered, SkillDirection.WANTED, wanted));
    }

    private void week(User user, DayOfWeek day, int startHour, int endHour) {
        weeks.computeIfAbsent(user.getId(), id -> new ArrayList<>())
                .add(Availability.builder()
                        .user(user)
                        .dayOfWeek(day)
                        .startTime(LocalTime.of(startHour, 0))
                        .endTime(LocalTime.of(endHour, 0))
                        .build());
    }

    private List<UserSkill> rows(UUID userId, SkillDirection direction) {
        User user = people.get(userId);
        return graph.getOrDefault(userId, Map.of()).getOrDefault(direction, List.of()).stream()
                .map(skill -> UserSkill.builder().user(user).skill(skill).direction(direction).build())
                .toList();
    }

    /** Wires the mocked repositories to answer from the little graph above. */
    private void wire() {
        lenient().when(userRepository.findById(me.getId())).thenReturn(Optional.of(me));
        lenient()
                .when(userSkillRepository.findApprovedByUserIdAndDirection(
                        any(UUID.class),
                        any(SkillDirection.class)))
                .thenAnswer(call -> rows(call.getArgument(0), call.getArgument(1)));
        lenient()
                .when(userSkillRepository.findApprovedBySkillIdInAndDirection(anySet(), any()))
                .thenAnswer(call -> {
                    Collection<UUID> skillIds = call.getArgument(0);
                    SkillDirection direction = call.getArgument(1);
                    List<UserSkill> found = new ArrayList<>();
                    for (UUID userId : graph.keySet()) {
                        rows(userId, direction).stream()
                                .filter(row -> skillIds.contains(row.getSkill().getId()))
                                .forEach(found::add);
                    }
                    return found;
                });
        lenient()
                .when(userSkillRepository.findApprovedByUserIdInAndDirection(anySet(), any()))
                .thenAnswer(call -> {
                    Collection<UUID> userIds = call.getArgument(0);
                    SkillDirection direction = call.getArgument(1);
                    List<UserSkill> found = new ArrayList<>();
                    userIds.forEach(id -> found.addAll(rows(id, direction)));
                    return found;
                });
        lenient().when(userRepository.findAllById(anySet())).thenAnswer(call -> {
            Collection<UUID> ids = call.getArgument(0);
            return ids.stream().map(people::get).filter(Objects::nonNull).toList();
        });
        lenient().when(reputationService.ofBatch(anySet())).thenAnswer(call -> {
            Set<UUID> ids = new HashSet<>(call.getArgument(0));
            Map<UUID, Reputation> result = new HashMap<>();
            ids.forEach(id -> result.put(id, Reputation.NONE));
            return result;
        });
        lenient().when(availabilityRepository.findByUserId(any(UUID.class)))
                .thenAnswer(call -> weeks.getOrDefault(call.getArgument(0), List.of()));
        lenient().when(availabilityRepository.findByUserIdIn(anySet())).thenAnswer(call -> {
            Collection<UUID> ids = call.getArgument(0);
            List<Availability> found = new ArrayList<>();
            ids.forEach(id -> found.addAll(weeks.getOrDefault(id, List.of())));
            return found;
        });
    }

    @BeforeEach
    void setUp() {
        graph.clear();
        weeks.clear();
        people.clear();
    }

    @Test
    @DisplayName("Closes a three-person ring where no two people are a mutual match")
    void shouldFindARingThatNoPairwiseMatcherWouldSee() {
        // I teach Java and want React. Nobody here wants what my own teacher
        // teaches, and nobody teaches what my own student wants: every pair in
        // this graph is a dead end, and the cycle is not.
        declare(me, List.of(java), List.of(react));
        declare(student, List.of(python), List.of(java));
        declare(teacher, List.of(react), List.of(python));
        wire();

        List<RingResponse> rings = ringService.findRings(me.getId(), 5);

        assertThat(rings).hasSize(1);
        var members = rings.getFirst().members();
        assertThat(members).extracting("displayName").containsExactly("Me", "Student", "Teacher");
        assertThat(members.get(0).teaches().name()).isEqualTo("Java");
        assertThat(members.get(1).teaches().name()).isEqualTo("Python");
        assertThat(members.get(2).teaches().name()).isEqualTo("React");
        // learns always mirrors the previous member's teaches, wrapping at the end.
        assertThat(members.get(0).learns().name()).isEqualTo("React");
        assertThat(members.get(1).learns().name()).isEqualTo("Java");
        assertThat(members.get(2).learns().name()).isEqualTo("Python");
    }

    @Test
    @DisplayName("A mutual pair is not reported as a ring")
    void shouldNotDressUpAMutualPairAsARing() {
        // The other person both wants my Java and teaches my React. That is a
        // plain mutual match and the feed already shows it; repeating it here
        // as a ring of two-and-a-half would be the same trade twice.
        declare(me, List.of(java), List.of(react));
        declare(student, List.of(react), List.of(java));
        wire();

        assertThat(ringService.findRings(me.getId(), 5)).isEmpty();
    }

    @Test
    @DisplayName("A ring is dropped when one consecutive pair can never meet")
    void shouldRejectARingWhoseWeakestLinkCannotMeet() {
        declare(me, List.of(java), List.of(react));
        declare(student, List.of(python), List.of(java));
        declare(teacher, List.of(react), List.of(python));
        // Everyone publishes a week, and the middle pair never intersects.
        week(me, DayOfWeek.MONDAY, 9, 12);
        week(student, DayOfWeek.MONDAY, 9, 12);
        week(teacher, DayOfWeek.MONDAY, 10, 12);
        wire();
        assertThat(ringService.findRings(me.getId(), 5)).hasSize(1);

        weeks.get(student.getId()).clear();
        week(student, DayOfWeek.SATURDAY, 9, 12);
        assertThat(ringService.findRings(me.getId(), 5)).isEmpty();
    }

    @Test
    @DisplayName("The reported strength is the weakest pair, not the average of the three")
    void shouldReportTheWeakestLinkRatherThanTheAverage() {
        declare(me, List.of(java), List.of(react));
        declare(student, List.of(python), List.of(java));
        declare(teacher, List.of(react), List.of(python));
        week(me, DayOfWeek.MONDAY, 9, 17); // 8h with the student, 8h with the teacher
        week(student, DayOfWeek.MONDAY, 9, 17);
        week(teacher, DayOfWeek.MONDAY, 9, 17);
        wire();
        assertThat(ringService.findRings(me.getId(), 5).getFirst().weakestLinkMinutes()).isEqualTo(480);

        // Narrow one single pair. Two easy pairs must not hide it.
        weeks.get(teacher.getId()).clear();
        week(teacher, DayOfWeek.MONDAY, 16, 17);
        assertThat(ringService.findRings(me.getId(), 5).getFirst().weakestLinkMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("Someone who has not published a week is unknown, not unavailable")
    void shouldKeepARingWhenOneMemberHasNoAvailabilityYet() {
        declare(me, List.of(java), List.of(react));
        declare(student, List.of(python), List.of(java));
        declare(teacher, List.of(react), List.of(python));
        week(me, DayOfWeek.MONDAY, 9, 12);
        week(teacher, DayOfWeek.SATURDAY, 9, 12); // never meets my Monday
        // The student has published nothing at all.
        wire();

        // The teacher/me edge is a real conflict, so this ring must go.
        assertThat(ringService.findRings(me.getId(), 5)).isEmpty();

        weeks.get(teacher.getId()).clear();
        week(teacher, DayOfWeek.MONDAY, 10, 12);
        // Now only the silent student is left, and silence does not block.
        assertThat(ringService.findRings(me.getId(), 5)).hasSize(1);
    }

    @Test
    @DisplayName("No ring is possible for someone who only teaches or only learns")
    void shouldAnswerNothingWhenTheUserCannotBeBothEnds() {
        declare(me, List.of(java), List.of());
        declare(student, List.of(python), List.of(java));
        wire();

        assertThat(ringService.findRings(me.getId(), 5)).isEmpty();
    }
}
