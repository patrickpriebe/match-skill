package com.matchskill.backend.service;

import com.matchskill.backend.repository.FeedbackRepository;
import com.matchskill.backend.repository.ReceivedReputation;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReputationService {

    private final FeedbackRepository feedbackRepository;

    public ReputationService(FeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    /** Only jointly published feedback from the other participant on completed exchanges contributes. */
    @Transactional(readOnly = true)
    public Reputation of(UUID userId) {
        return ofBatch(Set.of(userId)).get(userId);
    }

    /** Same aggregation as {@link #of(UUID)}, batched over many users in a single query. */
    @Transactional(readOnly = true)
    public Map<UUID, Reputation> ofBatch(Set<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Reputation> result = new HashMap<>();
        for (UUID userId : userIds) {
            result.put(userId, Reputation.NONE);
        }
        for (ReceivedReputation received : feedbackRepository.aggregateReceivedByUserIds(userIds)) {
            result.put(received.getUserId(), new Reputation(received.getAverageRating(), received.getRatingCount()));
        }
        return result;
    }
}
