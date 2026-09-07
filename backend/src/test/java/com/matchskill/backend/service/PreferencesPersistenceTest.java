package com.matchskill.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.matchskill.backend.dto.availability.AvailabilityWindowRequest;
import com.matchskill.backend.entity.Skill;
import com.matchskill.backend.entity.SkillStatus;
import com.matchskill.backend.entity.User;
import com.matchskill.backend.exception.ApiException;
import com.matchskill.backend.repository.SkillRepository;
import com.matchskill.backend.repository.UserRepository;
import com.matchskill.backend.security.JwtService;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(showSql = false)
@Import({UserSkillService.class, AvailabilityService.class, UserProfileService.class,
        ReputationService.class, AuthService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PreferencesPersistenceTest {

    @Autowired private UserRepository users;
    @Autowired private SkillRepository skills;
    @Autowired private UserSkillService userSkills;
    @Autowired private AvailabilityService availability;
    @Autowired private UserProfileService profiles;
    @Autowired private AuthService auth;
    @MockBean private PasswordEncoder encoder;
    @MockBean private JwtService jwt;

    @Test
    void repeatedSkillReplacementPreservesDualDirectionsWithoutUniqueViolation() {
        User user = user();
        Skill skill = skill(SkillStatus.APPROVED);
        userSkills.replaceMySkills(user.getId(), List.of(skill.getId()), List.of(skill.getId()));
        var second = userSkills.replaceMySkills(user.getId(),
                List.of(skill.getId(), skill.getId()), List.of(skill.getId()));
        assertThat(second.offered()).hasSize(1);
        assertThat(second.wanted()).hasSize(1);
        assertThat(auth.currentUser(user.getId()).skillsRegistered()).isTrue();
        assertThat(profiles.getProfile(user.getId()).skillsOffered()).hasSize(1);
    }

    @Test
    void rejectedReplacementKeepsExistingSkillsAndRegistrationState() {
        User user = user();
        Skill approved = skill(SkillStatus.APPROVED);
        Skill pending = skill(SkillStatus.PENDING_REVIEW);
        userSkills.replaceMySkills(user.getId(), List.of(approved.getId()), List.of());
        assertThatThrownBy(() -> userSkills.replaceMySkills(user.getId(),
                List.of(pending.getId()), List.of())).isInstanceOf(ApiException.class);
        assertThat(userSkills.getMySkills(user.getId()).offered())
                .singleElement().satisfies(entry -> assertThat(entry.skill().id()).isEqualTo(approved.getId()));
    }

    @Test
    void timeZoneAndWindowsCommitTogetherAndAppearInMeAndProfile() {
        User user = user();
        var result = availability.replaceMyAvailability(user.getId(), List.of(window(9, 10)), "America/New_York");
        assertThat(result).singleElement().satisfies(saved -> {
            assertThat(saved.id()).isNotNull();
            assertThat(saved.startTime()).isEqualTo(LocalTime.of(9, 0));
        });
        assertThat(auth.currentUser(user.getId()).timeZone()).isEqualTo("America/New_York");
        assertThat(profiles.getProfile(user.getId()).timeZone()).isEqualTo("America/New_York");
        availability.replaceMyAvailability(user.getId(), List.of(window(11, 12)));
        assertThat(auth.currentUser(user.getId()).timeZone()).isEqualTo("America/New_York");
        availability.replaceMyAvailability(user.getId(), List.of(), "UTC");
        assertThat(auth.currentUser(user.getId()).timeZone()).isEqualTo("UTC");
    }

    @Test
    void invalidZoneOrOverlappingWindowsCannotPartiallyReplaceSavedPreferences() {
        User user = user();
        availability.replaceMyAvailability(user.getId(), List.of(window(9, 10)), "America/Sao_Paulo");
        assertThatThrownBy(() -> availability.replaceMyAvailability(user.getId(),
                List.of(window(11, 12)), "Invalid/Zone")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> availability.replaceMyAvailability(user.getId(),
                List.of(window(11, 13), window(12, 14)), "America/New_York")).isInstanceOf(ApiException.class);
        assertThat(auth.currentUser(user.getId()).timeZone()).isEqualTo("America/Sao_Paulo");
        assertThat(availability.getMyAvailability(user.getId())).singleElement()
                .satisfies(saved -> assertThat(saved.startTime()).isEqualTo(LocalTime.of(9, 0)));
    }

    private User user() {
        return users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.test")
                .displayName("Test user").timeZone("UTC").build());
    }

    private Skill skill(SkillStatus status) {
        String name = UUID.randomUUID().toString();
        return skills.saveAndFlush(Skill.builder().name(name).slug(name).status(status).build());
    }

    private AvailabilityWindowRequest window(int start, int end) {
        return new AvailabilityWindowRequest(DayOfWeek.MONDAY, LocalTime.of(start, 0), LocalTime.of(end, 0));
    }
}
