package com.matchskill.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.matchskill.backend.entity.Skill;
import com.matchskill.backend.entity.SkillStatus;
import com.matchskill.backend.exception.ApiException;
import com.matchskill.backend.repository.SkillRepository;
import com.matchskill.backend.util.Slugs;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(showSql = false)
@Import(SkillService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SkillIdentityPersistenceTest {
    @Autowired private SkillRepository repository;
    @Autowired private SkillService service;

    @BeforeEach
    void clearIsolatedVocabulary() {
        repository.deleteAllInBatch();
    }

    @Test
    void legacyCppKeepsIdAndSlugWhileCAndCSharpGetDifferentIdentities() {
        Skill legacy = repository.saveAndFlush(Skill.builder().name("C++").slug("c")
                .status(SkillStatus.APPROVED).build());
        Skill c = service.suggest("C");
        Skill sharp = service.suggest("C#");
        Skill cpp = service.suggest("c++");
        assertThat(cpp.getId()).isEqualTo(legacy.getId());
        assertThat(cpp.getSlug()).isEqualTo("c");
        assertThat(cpp.getName()).isEqualTo("C++");
        assertThat(cpp.getStatus()).isEqualTo(SkillStatus.APPROVED);
        assertThat(c.getSlug()).isEqualTo(Slugs.fallbackSlug("C"));
        assertThat(sharp.getSlug()).isEqualTo("c~23");
        assertThat(List.of(c.getId(), sharp.getId(), cpp.getId())).doesNotHaveDuplicates();
        assertThat(service.suggest("C").getId()).isEqualTo(c.getId());
        assertThat(service.suggest("C#").getId()).isEqualTo(sharp.getId());
        assertThat(service.suggest("C++").getId()).isEqualTo(legacy.getId());
        assertThat(repository.count()).isEqualTo(3);
    }

    @Test
    void newSymbolTermsAndLiteralEnglishNamesNeverBecomeAliases() {
        List<Skill> created = List.of("C", "C++", "C#", "C plus plus", "C sharp", "Notepad++", "ABC++")
                .stream().map(service::suggest).toList();
        assertThat(created).extracting(Skill::getId).doesNotHaveDuplicates();
        assertThat(created).extracting(Skill::getSlug).doesNotHaveDuplicates();
        assertThat(created).allSatisfy(skill -> assertThat(skill.getStatus()).isEqualTo(SkillStatus.APPROVED));
    }

    @ParameterizedTest
    @ValueSource(strings = {"日本語", "Программирование", "العربية", "한국어"})
    void meaningfulUnicodeNamesPersistAndResolveAgain(String name) {
        Skill saved = service.suggest(name);
        assertThat(saved.getSlug()).isNotBlank();
        assertThat(service.suggest("  " + name + "  ").getId()).isEqualTo(saved.getId());
    }

    @Test
    void accentAndCaseVariantsClaimOneLegacyRowWithoutChangingItsSlug() {
        Skill legacy = repository.saveAndFlush(Skill.builder().name("Développement Web")
                .slug("developpement-web").status(SkillStatus.APPROVED).build());
        Skill resolved = service.suggest("DEVELOPPEMENT   WEB");
        assertThat(resolved.getId()).isEqualTo(legacy.getId());
        assertThat(resolved.getSlug()).isEqualTo(legacy.getSlug());
        assertThat(resolved.getName()).isEqualTo(legacy.getName());
        assertThat(resolved.getIdentityKey()).isEqualTo(Slugs.identityKey(legacy.getName()));
    }

    @Test
    void ambiguousLegacyRowsRemainUnchangedAndRequireReview() {
        Skill first = repository.saveAndFlush(Skill.builder().name("C++").slug("c")
                .status(SkillStatus.APPROVED).build());
        Skill second = repository.saveAndFlush(Skill.builder().name("c++").slug("cpp-legacy")
                .status(SkillStatus.PENDING_REVIEW).build());
        assertThatThrownBy(() -> service.suggest("C++")).isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("SKILL_IDENTITY_CONFLICT"));
        assertThat(repository.findById(first.getId()).orElseThrow().getIdentityKey()).isNull();
        assertThat(repository.findById(second.getId()).orElseThrow().getIdentityKey()).isNull();
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void rejectsMeaninglessNamesWithoutLeavingRows() {
        for (String name : List.of("+++", "###", "!!!", "🎨", "\u0301")) {
            assertThatThrownBy(() -> service.suggest(name)).isInstanceOf(ApiException.class);
        }
        assertThat(repository.count()).isZero();
    }

    @Test
    void concurrentEquivalentSuggestionsReturnTheSameCommittedRow() throws Exception {
        int count = 8;
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(count);
        try {
            var tasks = java.util.stream.IntStream.range(0, count).mapToObj(index -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for concurrent suggestions");
                }
                return service.suggest(index % 2 == 0 ? "C++" : "  c++  ").getId();
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var ids = new java.util.HashSet<UUID>();
            for (var task : tasks) {
                ids.add(task.get(10, TimeUnit.SECONDS));
            }
            assertThat(ids).hasSize(1);
            assertThat(repository.count()).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
