package com.matchskill.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.matchskill.backend.repository.FeedbackRepository;
import com.matchskill.backend.repository.ReceivedReputation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReputationServiceTest {

    @Mock
    private FeedbackRepository feedbackRepository;

    @InjectMocks
    private ReputationService reputationService;

    @Test
    void shouldReturnNoneWhenNoPublishedFeedbackReceived() {
        UUID userId = UUID.randomUUID();

        assertThat(reputationService.of(userId)).isEqualTo(Reputation.NONE);
        verify(feedbackRepository).aggregateReceivedByUserIds(Set.of(userId));
        verifyNoMoreInteractions(feedbackRepository);
    }

    @Test
    void shouldPreserveDatabaseAverageAndLongCount() {
        UUID userId = UUID.randomUUID();
        long largeCount = (long) Integer.MAX_VALUE + 1;
        when(feedbackRepository.aggregateReceivedByUserIds(Set.of(userId)))
                .thenReturn(List.of(aggregate(userId, 14.0 / 3, largeCount)));

        assertThat(reputationService.of(userId)).isEqualTo(new Reputation(14.0 / 3, largeCount));
        verify(feedbackRepository).aggregateReceivedByUserIds(Set.of(userId));
        verifyNoMoreInteractions(feedbackRepository);
    }

    @Test
    void shouldFillUnratedUsersWithZeroUsingOneAggregateQuery() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UUID unratedId = UUID.randomUUID();
        Set<UUID> ids = Set.of(firstId, secondId, unratedId);
        when(feedbackRepository.aggregateReceivedByUserIds(ids))
                .thenReturn(List.of(aggregate(firstId, 14.0 / 3, 3), aggregate(secondId, 3, 1)));

        Map<UUID, Reputation> result = reputationService.ofBatch(ids);

        assertThat(result).containsOnlyKeys(firstId, secondId, unratedId);
        assertThat(result.get(firstId)).isEqualTo(new Reputation(14.0 / 3, 3));
        assertThat(result.get(secondId)).isEqualTo(new Reputation(3, 1));
        assertThat(result.get(unratedId)).isEqualTo(Reputation.NONE);
        verify(feedbackRepository).aggregateReceivedByUserIds(ids);
        verifyNoMoreInteractions(feedbackRepository);
    }

    @Test
    void shouldSkipQueryForEmptyUserIds() {
        assertThat(reputationService.ofBatch(Set.of())).isEmpty();
        verifyNoInteractions(feedbackRepository);
    }

    private ReceivedReputation aggregate(UUID userId, double average, long count) {
        return new ReceivedReputation() {
            @Override
            public UUID getUserId() {
                return userId;
            }

            @Override
            public double getAverageRating() {
                return average;
            }

            @Override
            public long getRatingCount() {
                return count;
            }
        };
    }
}
