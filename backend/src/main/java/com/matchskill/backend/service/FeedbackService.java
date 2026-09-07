package com.matchskill.backend.service;

import com.matchskill.backend.dto.feedback.ExchangeFeedbackResponse;
import com.matchskill.backend.dto.feedback.FeedbackResponse;
import com.matchskill.backend.entity.Exchange;
import com.matchskill.backend.entity.ExchangeStatus;
import com.matchskill.backend.entity.Feedback;
import com.matchskill.backend.entity.User;
import com.matchskill.backend.exception.ApiException;
import com.matchskill.backend.repository.ExchangeRepository;
import com.matchskill.backend.repository.FeedbackRepository;
import com.matchskill.backend.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Feedback is only ever left on a COMPLETED exchange, by one of its two participants, once each. */
@Service
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final ExchangeRepository exchangeRepository;
    private final UserRepository userRepository;

    public FeedbackService(
            FeedbackRepository feedbackRepository,
            ExchangeRepository exchangeRepository,
            UserRepository userRepository) {
        this.feedbackRepository = feedbackRepository;
        this.exchangeRepository = exchangeRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public FeedbackResponse create(UUID authorId, UUID exchangeId, int rating, String comment) {
        Exchange exchange =
                exchangeRepository
                        .findByIdForUpdate(exchangeId)
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "EXCHANGE_NOT_FOUND", "Exchange not found"));

        boolean isParticipant =
                exchange.getRequester().getId().equals(authorId) || exchange.getReceiver().getId().equals(authorId);
        if (!isParticipant) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, "NOT_A_PARTICIPANT", "Not a participant of this exchange");
        }
        if (exchange.getStatus() != ExchangeStatus.COMPLETED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "EXCHANGE_NOT_COMPLETED",
                    "Feedback can only be left on a completed exchange");
        }
        if (feedbackRepository.existsByExchangeIdAndAuthorId(exchangeId, authorId)) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "FEEDBACK_ALREADY_SUBMITTED", "Feedback already submitted for this exchange");
        }

        User author =
                userRepository
                        .findById(authorId)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

        Feedback feedback =
                Feedback.builder().exchange(exchange).author(author).rating(rating).comment(comment).build();
        return FeedbackResponse.from(feedbackRepository.saveAndFlush(feedback));
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> getReceivedByUser(UUID userId, UUID viewerId) {
        return feedbackRepository.findReceivedForViewer(userId, viewerId).stream()
                .map(FeedbackResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ExchangeFeedbackResponse getForExchange(UUID viewerId, UUID exchangeId) {
        Exchange exchange = exchangeRepository.findById(exchangeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EXCHANGE_NOT_FOUND", "Exchange not found"));
        UUID requesterId = exchange.getRequester().getId();
        UUID receiverId = exchange.getReceiver().getId();
        if (!viewerId.equals(requesterId) && !viewerId.equals(receiverId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_A_PARTICIPANT", "Not a participant of this exchange");
        }
        if (exchange.getStatus() != ExchangeStatus.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT, "EXCHANGE_NOT_COMPLETED",
                    "Feedback can only be read on a completed exchange");
        }
        UUID counterpartId = viewerId.equals(requesterId) ? receiverId : requesterId;
        Feedback mine = null;
        Feedback theirs = null;
        for (Feedback feedback : feedbackRepository.findByExchangeId(exchangeId)) {
            if (feedback.getAuthor().getId().equals(viewerId)) {
                mine = feedback;
            } else if (feedback.getAuthor().getId().equals(counterpartId)) {
                theirs = feedback;
            }
        }
        return new ExchangeFeedbackResponse(
                mine == null ? null : FeedbackResponse.from(mine),
                mine == null || theirs == null ? null : FeedbackResponse.from(theirs),
                theirs != null);
    }
}
