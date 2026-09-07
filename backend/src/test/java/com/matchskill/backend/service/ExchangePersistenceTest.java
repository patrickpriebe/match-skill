package com.matchskill.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.matchskill.backend.config.MeetingUrlProperties;
import com.matchskill.backend.dto.exchange.ExchangeResponse;
import com.matchskill.backend.entity.Skill;
import com.matchskill.backend.entity.SkillDirection;
import com.matchskill.backend.entity.SkillStatus;
import com.matchskill.backend.entity.User;
import com.matchskill.backend.entity.UserSkill;
import com.matchskill.backend.entity.ExchangeStatus;
import com.matchskill.backend.exception.ApiException;
import com.matchskill.backend.repository.SkillRepository;
import com.matchskill.backend.repository.UserRepository;
import com.matchskill.backend.repository.UserSkillRepository;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "app.meeting-url.allowed-hosts=zoom.us,meet.google.com,teams.microsoft.com,whereby.com"
})
@Import({ExchangeService.class, MeetingUrlValidator.class})
@EnableConfigurationProperties(MeetingUrlProperties.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ExchangePersistenceTest {

    @Autowired
    private ExchangeService exchangeService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private UserSkillRepository userSkillRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID requesterId;
    private UUID receiverId;
    private UUID requestedSkillId;
    private UUID returnSkillId;

    @BeforeEach
    void createParticipantsAndSkills() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            User requester = userRepository.save(user("Requester"));
            User receiver = userRepository.save(user("Receiver"));
            Skill requestedSkill = skillRepository.save(skill("React"));
            Skill returnSkill = skillRepository.save(skill("Java"));
            userSkillRepository.saveAll(List.of(
                    assignment(receiver, requestedSkill, SkillDirection.OFFERED),
                    assignment(requester, returnSkill, SkillDirection.OFFERED),
                    assignment(receiver, returnSkill, SkillDirection.WANTED)));
            requesterId = requester.getId();
            receiverId = receiver.getId();
            requestedSkillId = requestedSkill.getId();
            returnSkillId = returnSkill.getId();
        });
    }

    @Test
    void returnsCompleteDtosAndPersistedTimestampsAfterEachTransaction() {
        ExchangeResponse created = exchangeService.create(requesterId, receiverId, requestedSkillId);

        assertThat(created.id()).isNotNull();
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.updatedAt()).isNotNull();
        assertThat(created.skillFromReceiver().name()).startsWith("React");
        assertThat(created.skillFromRequester()).isNull();
        assertThat(exchangeService.get(requesterId, created.id())).isEqualTo(created);

        ExchangeResponse accepted = exchangeService.accept(receiverId, created.id(), returnSkillId);
        assertThat(accepted.status()).isEqualTo(ExchangeStatus.ACCEPTED);
        assertThat(accepted.skillFromRequester().name()).startsWith("Java");
        assertThat(exchangeService.get(receiverId, created.id())).isEqualTo(accepted);

        Instant firstTime = Instant.parse("2030-09-05T12:00:00Z");
        ExchangeResponse scheduled = exchangeService.schedule(
                requesterId, created.id(), firstTime, "https://meet.google.com/abc-defg-hij");
        assertThat(scheduled.status()).isEqualTo(ExchangeStatus.SCHEDULED);
        assertThat(scheduled.scheduledAt()).isEqualTo(firstTime);

        Instant replacementTime = firstTime.plusSeconds(3600);
        ExchangeResponse rescheduled = exchangeService.schedule(
                receiverId, created.id(), replacementTime, "https://zoom.us/j/123");
        assertThat(rescheduled.scheduledAt()).isEqualTo(replacementTime);
        assertThat(rescheduled.meetingUrl()).isEqualTo("https://zoom.us/j/123");
        assertThat(exchangeService.get(requesterId, created.id())).isEqualTo(rescheduled);

        ExchangeResponse completed = exchangeService.complete(requesterId, created.id());
        assertThat(completed.status()).isEqualTo(ExchangeStatus.COMPLETED);
        assertThat(completed.updatedAt()).isAfterOrEqualTo(completed.createdAt());
        assertThat(exchangeService.get(receiverId, created.id())).isEqualTo(completed);
    }

    @ParameterizedTest(name = "Persists a complete meeting URL with {0} characters")
    @ValueSource(ints = {255, 256, 2048})
    void persistsMeetingUrlsThroughTheBoundaryWithoutTruncation(int length) {
        ExchangeResponse created = exchangeService.create(requesterId, receiverId, requestedSkillId);
        exchangeService.accept(receiverId, created.id(), returnSkillId);
        String meetingUrl = meetingUrlOfLength(length);
        Instant scheduledAt = Instant.parse("2030-09-05T12:00:00Z");

        ExchangeResponse scheduled = exchangeService.schedule(requesterId, created.id(), scheduledAt, meetingUrl);

        assertThat(scheduled.status()).isEqualTo(ExchangeStatus.SCHEDULED);
        assertThat(scheduled.meetingUrl()).hasSize(length).isEqualTo(meetingUrl);
        assertThat(scheduled.scheduledAt()).isEqualTo(scheduledAt);
        assertThat(exchangeService.get(receiverId, created.id())).isEqualTo(scheduled);
    }

    @ParameterizedTest
    @MethodSource("invalidMeetingUrls")
    void invalidReschedulingPreservesEveryCommittedExchangeField(String invalidUrl) {
        ExchangeResponse created = exchangeService.create(requesterId, receiverId, requestedSkillId);
        exchangeService.accept(receiverId, created.id(), returnSkillId);
        Instant originalTime = Instant.parse("2030-09-05T12:00:00Z");
        ExchangeResponse original = exchangeService.schedule(
                requesterId, created.id(), originalTime, meetingUrlOfLength(2048));

        assertThatThrownBy(() -> exchangeService.schedule(
                receiverId, created.id(), originalTime.plusSeconds(3600), invalidUrl))
                .isInstanceOf(ApiException.class)
                .satisfies(failure -> {
                    ApiException exception = (ApiException) failure;
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getCode()).isEqualTo("INVALID_MEETING_URL");
                });

        // The service transaction has ended; this new transaction reads the committed row.
        ExchangeResponse persisted = exchangeService.get(requesterId, created.id());
        assertThat(persisted).isEqualTo(original);
        assertThat(persisted.status()).isEqualTo(ExchangeStatus.SCHEDULED);
        assertThat(persisted.scheduledAt()).isEqualTo(originalTime);
        assertThat(persisted.meetingUrl()).isEqualTo(original.meetingUrl());
        assertThat(persisted.updatedAt()).isEqualTo(original.updatedAt());
    }

    @Test
    void listsRequestedExchangesWithNullableReturnSkillInStablePagesAndTwoQueries() {
        long prefix = UUID.randomUUID().getMostSignificantBits();
        List<UUID> ids = List.of(new UUID(prefix, 1), new UUID(prefix, 2), new UUID(prefix, 3));
        Timestamp sameCreationTime = Timestamp.from(Instant.parse("2026-09-05T10:00:00Z"));
        for (UUID id : ids) {
            ExchangeResponse created = exchangeService.create(requesterId, receiverId, requestedSkillId);
            jdbc.update("update exchanges set id = ?, created_at = ? where id = ?",
                    id, sameCreationTime, created.id());
        }

        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var firstPage = exchangeService.list(requesterId, null, PageRequest.of(0, 2));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getContent()).extracting(ExchangeResponse::id)
                .containsExactly(ids.get(2), ids.get(1));
        assertThat(firstPage.getContent()).allSatisfy(response -> {
            assertThat(response.skillFromReceiver().name()).startsWith("React");
            assertThat(response.skillFromRequester()).isNull();
        });
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);

        var secondPage = exchangeService.list(requesterId, ExchangeStatus.REQUESTED, PageRequest.of(1, 2));
        assertThat(secondPage.getContent()).extracting(ExchangeResponse::id).containsExactly(ids.get(0));
        assertThat(exchangeService.list(requesterId, ExchangeStatus.COMPLETED, PageRequest.of(0, 2)))
                .isEmpty();
    }

    @Test
    void declineAndCancelReturnDetachedSkillDetails() {
        ExchangeResponse requested = exchangeService.create(requesterId, receiverId, requestedSkillId);
        ExchangeResponse declined = exchangeService.decline(receiverId, requested.id());
        assertThat(declined.status()).isEqualTo(ExchangeStatus.DECLINED);
        assertThat(declined.skillFromReceiver().id()).isEqualTo(requestedSkillId);
        assertThat(exchangeService.get(requesterId, requested.id())).isEqualTo(declined);

        ExchangeResponse secondRequest = exchangeService.create(requesterId, receiverId, requestedSkillId);
        exchangeService.accept(receiverId, secondRequest.id(), returnSkillId);
        ExchangeResponse cancelled = exchangeService.cancel(requesterId, secondRequest.id());
        assertThat(cancelled.status()).isEqualTo(ExchangeStatus.CANCELLED);
        assertThat(cancelled.skillFromRequester().id()).isEqualTo(returnSkillId);
        assertThat(exchangeService.get(receiverId, secondRequest.id())).isEqualTo(cancelled);
    }

    @Test
    void concurrentCancelWaitsForCompletionAndRejectsTheCommittedTerminalState() throws Exception {
        ExchangeResponse requested = exchangeService.create(requesterId, receiverId, requestedSkillId);
        exchangeService.accept(receiverId, requested.id(), returnSkillId);
        exchangeService.schedule(requesterId, requested.id(), Instant.parse("2030-09-05T12:00:00Z"),
                "https://zoom.us/j/123");

        CountDownLatch completionFlushed = new CountDownLatch(1);
        CountDownLatch releaseCompletion = new CountDownLatch(1);
        CountDownLatch cancellationStarted = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var completion = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                ExchangeResponse response = exchangeService.complete(requesterId, requested.id());
                completionFlushed.countDown();
                await(releaseCompletion);
                return response;
            }));
            assertThat(completionFlushed.await(5, TimeUnit.SECONDS)).isTrue();

            var cancellation = executor.submit(() -> {
                cancellationStarted.countDown();
                try {
                    exchangeService.cancel(receiverId, requested.id());
                    return null;
                } catch (ApiException exception) {
                    return exception;
                }
            });
            assertThat(cancellationStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> cancellation.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseCompletion.countDown();
            assertThat(completion.get(5, TimeUnit.SECONDS).status()).isEqualTo(ExchangeStatus.COMPLETED);
            ApiException conflict = cancellation.get(5, TimeUnit.SECONDS);
            assertThat(conflict).isNotNull();
            assertThat(conflict.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(conflict.getCode()).isEqualTo("INVALID_STATE_TRANSITION");
            assertThat(exchangeService.get(requesterId, requested.id()).status())
                    .isEqualTo(ExchangeStatus.COMPLETED);
        } finally {
            releaseCompletion.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release the exchange transaction");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to release the exchange transaction", exception);
        }
    }

    private static Stream<String> invalidMeetingUrls() {
        return Stream.of(null, "", " ", "not a URL", "http://zoom.us/j/123",
                "https://zoom.us.attacker.example/j/123", meetingUrlOfLength(2049));
    }

    private static String meetingUrlOfLength(int length) {
        String prefix = "https://teams.microsoft.com/l/meetup-join/";
        return prefix + "a".repeat(length - prefix.length());
    }

    private static User user(String name) {
        return User.builder().email(UUID.randomUUID() + "@example.test")
                .displayName(name).timeZone("UTC").build();
    }

    private static Skill skill(String name) {
        String suffix = UUID.randomUUID().toString();
        return Skill.builder().name(name + " " + suffix).slug(name.toLowerCase() + "-" + suffix)
                .status(SkillStatus.APPROVED).build();
    }

    private static UserSkill assignment(User user, Skill skill, SkillDirection direction) {
        return UserSkill.builder().user(user).skill(skill).direction(direction).build();
    }
}
