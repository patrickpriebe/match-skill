package com.matchskill.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.matchskill.backend.dto.skill.MySkillsResponse;
import com.matchskill.backend.entity.Skill;
import com.matchskill.backend.entity.SkillDirection;
import com.matchskill.backend.entity.SkillStatus;
import com.matchskill.backend.entity.User;
import com.matchskill.backend.entity.UserSkill;
import com.matchskill.backend.exception.ApiException;
import com.matchskill.backend.repository.SkillRepository;
import com.matchskill.backend.repository.UserRepository;
import com.matchskill.backend.repository.UserSkillRepository;
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
class UserSkillServiceTest {

    @Mock
    private UserSkillRepository userSkillRepository;

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserSkillService userSkillService;

    @Test
    @DisplayName("getMySkills: returns mapped OFFERED and WANTED skills")
    void shouldReturnMySkillsGroupedByDirection() {
        UUID userId = UUID.randomUUID();
        Skill java = Skill.builder().id(UUID.randomUUID()).name("Java").slug("java").status(SkillStatus.APPROVED).build();
        Skill react = Skill.builder().id(UUID.randomUUID()).name("React").slug("react").status(SkillStatus.APPROVED).build();

        User user = User.builder().id(userId).build();
        UserSkill offered = UserSkill.builder().id(UUID.randomUUID()).user(user).skill(java).direction(SkillDirection.OFFERED).build();
        UserSkill wanted = UserSkill.builder().id(UUID.randomUUID()).user(user).skill(react).direction(SkillDirection.WANTED).build();

        when(userSkillRepository.findByUserIdAndDirection(userId, SkillDirection.OFFERED)).thenReturn(List.of(offered));
        when(userSkillRepository.findByUserIdAndDirection(userId, SkillDirection.WANTED)).thenReturn(List.of(wanted));

        MySkillsResponse response = userSkillService.getMySkills(userId);

        assertThat(response.offered()).hasSize(1);
        assertThat(response.offered().get(0).skill().name()).isEqualTo("Java");
        assertThat(response.wanted()).hasSize(1);
        assertThat(response.wanted().get(0).skill().name()).isEqualTo("React");
    }

    @Test
    @DisplayName("replaceMySkills: wipes old entries, saves new ones with deduplication, sets skillsRegistered=true")
    void shouldReplaceMySkillsWholesale() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).skillsRegistered(false).build();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

        UUID skill1Id = UUID.randomUUID();
        UUID skill2Id = UUID.randomUUID();
        Skill skill1 = Skill.builder().id(skill1Id).name("Java").status(SkillStatus.APPROVED).build();
        Skill skill2 = Skill.builder().id(skill2Id).name("React").status(SkillStatus.APPROVED).build();

        when(skillRepository.findAllById(any())).thenReturn(List.of(skill1, skill2));


        // Pass duplicates in list to test LinkedHashSet deduplication
        List<UUID> offered = List.of(skill1Id, skill1Id);
        // Same skill can also be in WANTED
        List<UUID> wanted = List.of(skill1Id, skill2Id);

        userSkillService.replaceMySkills(userId, offered, wanted);

        verify(userSkillRepository).deleteByUserId(userId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserSkill>> entriesCaptor = ArgumentCaptor.forClass(List.class);
        verify(userSkillRepository).saveAll(entriesCaptor.capture());
        List<UserSkill> savedEntries = entriesCaptor.getValue();

        // 1 unique offered + 2 unique wanted = 3 total entries
        assertThat(savedEntries).hasSize(3);
        assertThat(savedEntries.stream().filter(e -> e.getDirection() == SkillDirection.OFFERED)).hasSize(1);
        assertThat(savedEntries.stream().filter(e -> e.getDirection() == SkillDirection.WANTED)).hasSize(2);

        // User marked as skillsRegistered
        assertThat(user.isSkillsRegistered()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Regression P0: reject PENDING_REVIEW skill when user attempts to register it via replaceMySkills")
    void shouldRejectPendingReviewSkillOnReplaceMySkills() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

        UUID pendingSkillId = UUID.randomUUID();
        Skill pendingSkill = Skill.builder()
                .id(pendingSkillId)
                .name("UnapprovedSkill")
                .status(SkillStatus.PENDING_REVIEW)
                .build();
        when(skillRepository.findAllById(any())).thenReturn(List.of(pendingSkill));

        assertThatThrownBy(() -> userSkillService.replaceMySkills(userId, List.of(pendingSkillId), List.of()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getCode()).isEqualTo("SKILL_NOT_APPROVED");
                });

        verify(userSkillRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("replaceMySkills: throws 404 when user not found")
    void shouldThrowNotFoundWhenUserMissingInReplace() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userSkillService.replaceMySkills(userId, List.of(), List.of()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getCode()).isEqualTo("USER_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("replaceMySkills: throws 400 when skill does not exist")
    void shouldThrowBadRequestWhenSkillDoesNotExist() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

        UUID missingSkillId = UUID.randomUUID();
        when(skillRepository.findAllById(any())).thenReturn(List.of());

        assertThatThrownBy(() -> userSkillService.replaceMySkills(userId, List.of(missingSkillId), List.of()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getCode()).isEqualTo("SKILL_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("deleteMySkill: successfully deletes when owned by user")
    void shouldDeleteMySkillWhenOwnedByUser() {
        UUID userId = UUID.randomUUID();
        UUID userSkillId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        UserSkill userSkill = UserSkill.builder().id(userSkillId).user(user).build();

        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(User.builder().id(userId).build()));
        when(userSkillRepository.findById(userSkillId)).thenReturn(Optional.of(userSkill));

        userSkillService.deleteMySkill(userId, userSkillId);

        verify(userSkillRepository).delete(userSkill);
    }

    @Test
    @DisplayName("deleteMySkill: throws 404 when entry belongs to another user")
    void shouldThrowNotFoundWhenDeletingAnotherUsersSkill() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID userSkillId = UUID.randomUUID();
        User otherUser = User.builder().id(otherUserId).build();
        UserSkill userSkill = UserSkill.builder().id(userSkillId).user(otherUser).build();

        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(User.builder().id(userId).build()));
        when(userSkillRepository.findById(userSkillId)).thenReturn(Optional.of(userSkill));

        assertThatThrownBy(() -> userSkillService.deleteMySkill(userId, userSkillId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getCode()).isEqualTo("USER_SKILL_NOT_FOUND");
                });

        verify(userSkillRepository, never()).delete(any());
    }
}
