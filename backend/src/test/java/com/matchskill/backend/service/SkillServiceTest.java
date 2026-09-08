package com.matchskill.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.matchskill.backend.entity.Skill;
import com.matchskill.backend.entity.SkillStatus;
import com.matchskill.backend.exception.ApiException;
import com.matchskill.backend.repository.SkillRepository;
import com.matchskill.backend.util.Slugs;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class SkillServiceTest {

    @Mock
    private SkillRepository skillRepository;

    @InjectMocks
    private SkillService skillService;

    @Test
    @DisplayName("Search: queries only APPROVED skills with given term")
    void shouldSearchOnlyApprovedSkills() {
        Pageable pageable = PageRequest.of(0, 10);
        Skill skill = Skill.builder()
                .id(UUID.randomUUID())
                .name("Java")
                .slug("java")
                .status(SkillStatus.APPROVED)
                .build();
        when(skillRepository.findByStatusAndNameContainingIgnoreCase(
                        SkillStatus.APPROVED, "jav", pageable))
                .thenReturn(new PageImpl<>(List.of(skill)));

        Page<Skill> result = skillService.search("jav", pageable);

        assertThat(result.getContent()).containsExactly(skill);
        verify(skillRepository).findByStatusAndNameContainingIgnoreCase(SkillStatus.APPROVED, "jav", pageable);
    }

    @Test
    @DisplayName("Search with null query replaces with empty string")
    void shouldHandleNullQueryInSearch() {
        Pageable pageable = PageRequest.of(0, 10);
        when(skillRepository.findByStatusAndNameContainingIgnoreCase(
                        SkillStatus.APPROVED, "", pageable))
                .thenReturn(Page.empty());

        Page<Skill> result = skillService.search(null, pageable);

        assertThat(result).isEmpty();
        verify(skillRepository).findByStatusAndNameContainingIgnoreCase(SkillStatus.APPROVED, "", pageable);
    }

    @Test
    @DisplayName("Suggest: returns existing canonical identity without rewriting public fields")
    void shouldReturnExistingSkillWhenIdentityAlreadyExists() {
        Skill existing = Skill.builder()
                .id(UUID.randomUUID())
                .name("React")
                .slug("react")
                .identityKey(Slugs.identityKey("React"))
                .status(SkillStatus.APPROVED)
                .build();
        when(skillRepository.findIdentityCandidates(eq(Slugs.identityKey("React")), any(), eq("React")))
                .thenReturn(List.of(existing));

        Skill result = skillService.suggest("  React  ");

        assertThat(result).isSameAs(existing);
        verify(skillRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Suggest: creates new skill as APPROVED when slug does not exist")
    void shouldCreateNewSkillWithApprovedStatus() {
        when(skillRepository.findIdentityCandidates(any(), any(), eq("Kubernetes"))).thenReturn(List.of());
        when(skillRepository.saveAndFlush(any(Skill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Skill result = skillService.suggest("  Kubernetes  ");

        assertThat(result.getName()).isEqualTo("Kubernetes");
        assertThat(result.getSlug()).isEqualTo("kubernetes");
        assertThat(result.getStatus()).isEqualTo(SkillStatus.APPROVED);
        assertThat(result.getIdentityKey()).isEqualTo(Slugs.identityKey("Kubernetes"));

        ArgumentCaptor<Skill> captor = ArgumentCaptor.forClass(Skill.class);
        verify(skillRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SkillStatus.APPROVED);
    }

    @Test
    void rejectsMeaninglessNameBeforeQueryingOrWriting() {
        for (String name : List.of("+++", "#", "---", "🎨")) {
            assertThatThrownBy(() -> skillService.suggest(name)).isInstanceOf(ApiException.class)
                    .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("INVALID_SKILL_NAME"));
        }
        verify(skillRepository, never()).findIdentityCandidates(any(), any(), any());
        verify(skillRepository, never()).saveAndFlush(any());
    }

    @Test
    void doesNotTrustAnIdentityKeyWhoseNameHasDifferentMeaning() {
        Skill corrupt = Skill.builder().name("C++").slug("c")
                .identityKey(Slugs.identityKey("C")).build();
        when(skillRepository.findIdentityCandidates(any(), any(), eq("C"))).thenReturn(List.of(corrupt));
        assertThatThrownBy(() -> skillService.suggest("C")).isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("SKILL_IDENTITY_CONFLICT"));
        verify(skillRepository, never()).saveAndFlush(any());
    }
}
