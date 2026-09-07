package com.matchskill.backend.dto.feedback;

import com.matchskill.backend.entity.Feedback;
import java.time.Instant;
import java.util.UUID;

public record FeedbackResponse(
        UUID id, UUID exchangeId, UUID authorId, int rating, String comment, Instant createdAt) {

    public static FeedbackResponse from(Feedback feedback) {
        return new FeedbackResponse(
                feedback.getId(),
                feedback.getExchange().getId(),
                feedback.getAuthor().getId(),
                feedback.getRating(),
                feedback.getComment(),
                feedback.getCreatedAt());
    }
}
