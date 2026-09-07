package com.matchskill.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.matchskill.backend.dto.feedback.ExchangeFeedbackResponse;
import com.matchskill.backend.dto.feedback.FeedbackResponse;
import com.matchskill.backend.entity.Exchange;
import com.matchskill.backend.entity.ExchangeStatus;
import com.matchskill.backend.entity.ExchangeStrength;
import com.matchskill.backend.entity.Feedback;
import com.matchskill.backend.entity.Skill;
import com.matchskill.backend.entity.SkillStatus;
import com.matchskill.backend.entity.User;
import com.matchskill.backend.exception.ApiException;
import com.matchskill.backend.repository.ExchangeRepository;
import com.matchskill.backend.repository.FeedbackRepository;
import com.matchskill.backend.repository.SkillRepository;
import com.matchskill.backend.repository.UserRepository;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(showSql = false, properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({FeedbackService.class, ReputationService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FeedbackPersistenceTest {

    @Autowired
    private FeedbackService feedbackService;

    @Autowired
    private ReputationService reputationService;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private ExchangeRepository exchangeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID requesterId;
    private UUID receiverId;
    private UUID strangerId;
    private UUID exchangeId;

    @BeforeEach
    void createCompletedExchange() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            User requester = userRepository.save(user("Requester"));
            User receiver = userRepository.save(user("Receiver"));
            User stranger = userRepository.save(user("Stranger"));
            String skillName = "Java-" + UUID.randomUUID();
            Skill skill = skillRepository.save(Skill.builder().name(skillName).slug(skillName)
                    .status(SkillStatus.APPROVED).build());
            Exchange exchange = exchangeRepository.save(Exchange.builder()
                    .requester(requester).receiver(receiver)
                    .skillFromReceiver(skill).skillFromRequester(skill)
                    .strength(ExchangeStrength.MUTUAL).status(ExchangeStatus.COMPLETED)
                    .scheduledAt(Instant.parse("2026-09-01T12:00:00Z"))
                    .meetingUrl("https://zoom.us/j/123").build());
            requesterId = requester.getId();
            receiverId = receiver.getId();
            strangerId = stranger.getId();
            exchangeId = exchange.getId();
        });
    }

    @ParameterizedTest(name = "First reviewer is requester: {0}")
    @ValueSource(booleans = {true, false})
    void keepsOneReviewPrivateAndPublishesBothOnlyAfterTheSecondTransactionCommits(
            boolean firstReviewerIsRequester) throws Exception {
        UUID firstAuthorId = firstReviewerIsRequester ? requesterId : receiverId;
        UUID secondAuthorId = firstReviewerIsRequester ? receiverId : requesterId;
        FeedbackResponse first = feedbackService.create(firstAuthorId, exchangeId, 1, "Private first review");

        assertThat(first.id()).isNotNull();
        assertThat(first.createdAt()).isNotNull();
        assertUnilateralVisibility(firstAuthorId, secondAuthorId, first);

        CountDownLatch secondReviewFlushed = new CountDownLatch(1);
        CountDownLatch releaseSecondReview = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var secondSubmission = executor.submit(() ->
                    new TransactionTemplate(transactionManager).execute(status -> {
                        FeedbackResponse response = feedbackService.create(
                                secondAuthorId, exchangeId, 5, "Private until this transaction commits");
                        secondReviewFlushed.countDown();
                        await(releaseSecondReview);
                        return response;
                    }));
            assertThat(secondReviewFlushed.await(5, TimeUnit.SECONDS)).isTrue();

            // Independent read transactions must not expose the uncommitted second submission.
            assertUnilateralVisibility(firstAuthorId, secondAuthorId, first);

            releaseSecondReview.countDown();
            FeedbackResponse second = secondSubmission.get(5, TimeUnit.SECONDS);
            assertThat(feedbackService.getForExchange(firstAuthorId, exchangeId))
                    .isEqualTo(new ExchangeFeedbackResponse(first, second, true));
            assertThat(feedbackService.getForExchange(secondAuthorId, exchangeId))
                    .isEqualTo(new ExchangeFeedbackResponse(second, first, true));

            for (UUID viewerId : Set.of(firstAuthorId, secondAuthorId, strangerId)) {
                assertThat(feedbackService.getReceivedByUser(secondAuthorId, viewerId)).containsExactly(first);
                assertThat(feedbackService.getReceivedByUser(firstAuthorId, viewerId)).containsExactly(second);
            }
            assertThat(reputationService.of(firstAuthorId)).isEqualTo(new Reputation(5.0, 1));
            assertThat(reputationService.of(secondAuthorId)).isEqualTo(new Reputation(1.0, 1));
            assertThat(reputationService.ofBatch(Set.of(firstAuthorId, secondAuthorId, strangerId)))
                    .containsEntry(firstAuthorId, new Reputation(5.0, 1))
                    .containsEntry(secondAuthorId, new Reputation(1.0, 1))
                    .containsEntry(strangerId, Reputation.NONE);
        } finally {
            releaseSecondReview.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void rejectsThirdPartyExchangeReadsAndSubmissions() {
        assertRejected(() -> feedbackService.getForExchange(strangerId, exchangeId),
                HttpStatus.FORBIDDEN, "NOT_A_PARTICIPANT");
        assertRejected(() -> feedbackService.create(strangerId, exchangeId, 5, "Not a participant"),
                HttpStatus.FORBIDDEN, "NOT_A_PARTICIPANT");
        assertThat(feedbackRepository.findByExchangeId(exchangeId)).isEmpty();
    }

    @Test
    void returnsNotFoundForUnknownExchangeReadsAndSubmissions() {
        UUID missingId = UUID.randomUUID();
        assertRejected(() -> feedbackService.getForExchange(requesterId, missingId),
                HttpStatus.NOT_FOUND, "EXCHANGE_NOT_FOUND");
        assertRejected(() -> feedbackService.create(requesterId, missingId, 5, "Missing exchange"),
                HttpStatus.NOT_FOUND, "EXCHANGE_NOT_FOUND");
    }

    @ParameterizedTest(name = "Feedback is unavailable while exchange is {0}")
    @EnumSource(value = ExchangeStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "COMPLETED")
    void rejectsReadsAndSubmissionsBeforeCompletion(ExchangeStatus exchangeStatus) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Exchange exchange = exchangeRepository.findById(exchangeId).orElseThrow();
            exchange.setStatus(exchangeStatus);
        });

        for (UUID participantId : Set.of(requesterId, receiverId)) {
            assertRejected(() -> feedbackService.getForExchange(participantId, exchangeId),
                    HttpStatus.CONFLICT, "EXCHANGE_NOT_COMPLETED");
            assertRejected(() -> feedbackService.create(participantId, exchangeId, 5, "Too early"),
                    HttpStatus.CONFLICT, "EXCHANGE_NOT_COMPLETED");
        }
        assertThat(feedbackRepository.findByExchangeId(exchangeId)).isEmpty();
    }

    @Test
    void historicalUnilateralFeedbackRemainsStoredButPrivateAndExcludedFromReputation() {
        FeedbackResponse legacy = new TransactionTemplate(transactionManager).execute(status -> {
            Feedback feedback = Feedback.builder()
                    .exchange(exchangeRepository.findById(exchangeId).orElseThrow())
                    .author(userRepository.getReferenceById(requesterId))
                    .rating(2).comment("Legacy unilateral review").build();
            return FeedbackResponse.from(feedbackRepository.saveAndFlush(feedback));
        });

        assertThat(feedbackRepository.findByExchangeId(exchangeId)).hasSize(1);
        assertUnilateralVisibility(requesterId, receiverId, legacy);
    }

    @ParameterizedTest(name = "Duplicate reviewer is requester: {0}")
    @ValueSource(booleans = {true, false})
    void concurrentDuplicateSubmissionsReturnOneSuccessAndOneDomainConflict(boolean authorIsRequester)
            throws Exception {
        UUID authorId = authorIsRequester ? requesterId : receiverId;
        CountDownLatch firstSubmissionFlushed = new CountDownLatch(1);
        CountDownLatch releaseFirstSubmission = new CountDownLatch(1);
        CountDownLatch duplicateStarted = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var firstSubmission = executor.submit(() ->
                    new TransactionTemplate(transactionManager).execute(status -> {
                        FeedbackResponse response = feedbackService.create(authorId, exchangeId, 4, "First submission");
                        firstSubmissionFlushed.countDown();
                        await(releaseFirstSubmission);
                        return response;
                    }));
            assertThat(firstSubmissionFlushed.await(5, TimeUnit.SECONDS)).isTrue();

            var duplicate = executor.submit(() -> {
                duplicateStarted.countDown();
                try {
                    feedbackService.create(authorId, exchangeId, 1, "Concurrent duplicate");
                    return null;
                } catch (ApiException exception) {
                    return exception;
                }
            });
            assertThat(duplicateStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> duplicate.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseFirstSubmission.countDown();
            FeedbackResponse saved = firstSubmission.get(5, TimeUnit.SECONDS);
            ApiException conflict = duplicate.get(5, TimeUnit.SECONDS);
            assertThat(conflict).isNotNull();
            assertThat(conflict.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(conflict.getCode()).isEqualTo("FEEDBACK_ALREADY_SUBMITTED");
            assertThat(feedbackRepository.findByExchangeId(exchangeId)).hasSize(1);
            assertThat(feedbackService.getForExchange(authorId, exchangeId).mine()).isEqualTo(saved);
            assertThat(saved.rating()).isEqualTo(4);
            assertThat(saved.comment()).isEqualTo("First submission");
            assertThat(reputationService.ofBatch(Set.of(requesterId, receiverId)))
                    .containsEntry(requesterId, Reputation.NONE).containsEntry(receiverId, Reputation.NONE);
        } finally {
            releaseFirstSubmission.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void assertUnilateralVisibility(UUID authorId, UUID recipientId, FeedbackResponse feedback) {
        assertThat(feedbackService.getForExchange(authorId, exchangeId))
                .isEqualTo(new ExchangeFeedbackResponse(feedback, null, false));
        assertThat(feedbackService.getForExchange(recipientId, exchangeId))
                .isEqualTo(new ExchangeFeedbackResponse(null, null, true));
        assertThat(feedbackService.getReceivedByUser(recipientId, authorId)).containsExactly(feedback);
        assertThat(feedbackService.getReceivedByUser(recipientId, recipientId)).isEmpty();
        assertThat(feedbackService.getReceivedByUser(recipientId, strangerId)).isEmpty();
        assertThat(feedbackService.getReceivedByUser(authorId, authorId)).isEmpty();
        assertThat(reputationService.of(authorId)).isEqualTo(Reputation.NONE);
        assertThat(reputationService.of(recipientId)).isEqualTo(Reputation.NONE);
        assertThat(reputationService.ofBatch(Set.of(authorId, recipientId, strangerId)))
                .containsEntry(authorId, Reputation.NONE)
                .containsEntry(recipientId, Reputation.NONE)
                .containsEntry(strangerId, Reputation.NONE);
    }

    private static void assertRejected(ThrowingCallable action, HttpStatus status, String code) {
        assertThatThrownBy(action).isInstanceOf(ApiException.class).satisfies(failure -> {
            ApiException exception = (ApiException) failure;
            assertThat(exception.getStatus()).isEqualTo(status);
            assertThat(exception.getCode()).isEqualTo(code);
        });
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release the feedback transaction");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to release the feedback transaction", exception);
        }
    }

    private static User user(String name) {
        return User.builder().email(UUID.randomUUID() + "@example.test")
                .displayName(name).timeZone("UTC").build();
    }
}
