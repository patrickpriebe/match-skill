package com.matchskill.backend.service;

import com.matchskill.backend.dto.exchange.ExchangeResponse;
import com.matchskill.backend.entity.Exchange;
import com.matchskill.backend.entity.ExchangeStatus;
import com.matchskill.backend.entity.ExchangeStrength;
import com.matchskill.backend.entity.Skill;
import com.matchskill.backend.entity.SkillDirection;
import com.matchskill.backend.entity.User;
import com.matchskill.backend.exception.ApiException;
import com.matchskill.backend.repository.ExchangeRepository;
import com.matchskill.backend.repository.SkillRepository;
import com.matchskill.backend.repository.UserRepository;
import com.matchskill.backend.repository.UserSkillRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The exchange lifecycle: REQUESTED -&gt; DECLINED | ACCEPTED -&gt; SCHEDULED
 * -&gt; COMPLETED, with CANCELLED reachable from ACCEPTED or SCHEDULED. Only
 * the receiver may accept or decline; either side may schedule, complete or
 * cancel.
 */
@Service
public class ExchangeService {

    private final ExchangeRepository exchangeRepository;
    private final UserRepository userRepository;
    private final SkillRepository skillRepository;
    private final UserSkillRepository userSkillRepository;
    private final MeetingUrlValidator meetingUrlValidator;

    public ExchangeService(
            ExchangeRepository exchangeRepository,
            UserRepository userRepository,
            SkillRepository skillRepository,
            UserSkillRepository userSkillRepository,
            MeetingUrlValidator meetingUrlValidator) {
        this.exchangeRepository = exchangeRepository;
        this.userRepository = userRepository;
        this.skillRepository = skillRepository;
        this.userSkillRepository = userSkillRepository;
        this.meetingUrlValidator = meetingUrlValidator;
    }

    @Transactional
    public ExchangeResponse create(UUID requesterId, UUID receiverId, UUID skillFromReceiverId) {
        if (requesterId.equals(receiverId)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "CANNOT_EXCHANGE_WITH_SELF", "Cannot request an exchange with yourself");
        }
        User requester = requireUser(requesterId);
        User receiver = requireUser(receiverId);
        Skill skillFromReceiver = requireSkill(skillFromReceiverId);

        if (!offers(receiverId, skillFromReceiverId)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "SKILL_NOT_OFFERED",
                    "The receiver does not offer that skill");
        }

        boolean mutual = requesterOffersSomethingReceiverWants(requesterId, receiverId);

        Exchange exchange =
                Exchange.builder()
                        .requester(requester)
                        .receiver(receiver)
                        .skillFromReceiver(skillFromReceiver)
                        .strength(mutual ? ExchangeStrength.MUTUAL : ExchangeStrength.PARTIAL)
                        .status(ExchangeStatus.REQUESTED)
                        .build();
        return saveResponse(exchange);
    }

    @Transactional(readOnly = true)
    public ExchangeResponse get(UUID userId, UUID exchangeId) {
        Exchange exchange = requireExchange(exchangeId);
        requireParticipant(exchange, userId);
        return ExchangeResponse.from(exchange);
    }

    @Transactional(readOnly = true)
    public Page<ExchangeResponse> list(UUID userId, ExchangeStatus status, Pageable pageable) {
        Pageable ordered = PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return exchangeRepository.findByParticipant(userId, status, ordered).map(ExchangeResponse::from);
    }

    @Transactional
    public ExchangeResponse accept(UUID userId, UUID exchangeId, UUID skillFromRequesterId) {
        Exchange exchange = requireExchangeForUpdate(exchangeId);
        requireReceiver(exchange, userId);
        requireStatus(exchange, ExchangeStatus.REQUESTED);

        if (!offers(exchange.getRequester().getId(), skillFromRequesterId)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "SKILL_NOT_OFFERED",
                    "The requester does not offer that skill");
        }
        exchange.setSkillFromRequester(requireSkill(skillFromRequesterId));
        exchange.setStatus(ExchangeStatus.ACCEPTED);
        return saveResponse(exchange);
    }

    @Transactional
    public ExchangeResponse decline(UUID userId, UUID exchangeId) {
        Exchange exchange = requireExchangeForUpdate(exchangeId);
        requireReceiver(exchange, userId);
        requireStatus(exchange, ExchangeStatus.REQUESTED);
        exchange.setStatus(ExchangeStatus.DECLINED);
        return saveResponse(exchange);
    }

    @Transactional
    public ExchangeResponse schedule(UUID userId, UUID exchangeId, Instant scheduledAt, String meetingUrl) {
        Exchange exchange = requireExchangeForUpdate(exchangeId);
        requireParticipant(exchange, userId);
        requireStatus(exchange, ExchangeStatus.ACCEPTED, ExchangeStatus.SCHEDULED);
        meetingUrlValidator.validate(meetingUrl);
        exchange.setScheduledAt(scheduledAt);
        exchange.setMeetingUrl(meetingUrl);
        exchange.setStatus(ExchangeStatus.SCHEDULED);
        return saveResponse(exchange);
    }

    @Transactional
    public ExchangeResponse complete(UUID userId, UUID exchangeId) {
        Exchange exchange = requireExchangeForUpdate(exchangeId);
        requireParticipant(exchange, userId);
        requireStatus(exchange, ExchangeStatus.SCHEDULED);
        exchange.setStatus(ExchangeStatus.COMPLETED);
        return saveResponse(exchange);
    }

    @Transactional
    public ExchangeResponse cancel(UUID userId, UUID exchangeId) {
        Exchange exchange = requireExchangeForUpdate(exchangeId);
        requireParticipant(exchange, userId);
        requireStatus(exchange, ExchangeStatus.ACCEPTED, ExchangeStatus.SCHEDULED);
        exchange.setStatus(ExchangeStatus.CANCELLED);
        return saveResponse(exchange);
    }

    private ExchangeResponse saveResponse(Exchange exchange) {
        // Flush before mapping so persistence timestamps are present in the response.
        return ExchangeResponse.from(exchangeRepository.saveAndFlush(exchange));
    }

    private boolean offers(UUID userId, UUID skillId) {
        return userSkillRepository.findByUserIdAndDirection(userId, SkillDirection.OFFERED).stream()
                .anyMatch(us -> us.getSkill().getId().equals(skillId));
    }

    private boolean requesterOffersSomethingReceiverWants(UUID requesterId, UUID receiverId) {
        Set<UUID> requesterOffered =
                userSkillRepository.findByUserIdAndDirection(requesterId, SkillDirection.OFFERED).stream()
                        .map(us -> us.getSkill().getId())
                        .collect(Collectors.toSet());
        return userSkillRepository.findByUserIdAndDirection(receiverId, SkillDirection.WANTED).stream()
                .anyMatch(us -> requesterOffered.contains(us.getSkill().getId()));
    }

    private User requireUser(UUID userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
    }

    private Skill requireSkill(UUID skillId) {
        return skillRepository
                .findById(skillId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SKILL_NOT_FOUND", "Skill not found"));
    }

    private Exchange requireExchange(UUID exchangeId) {
        return exchangeRepository
                .findById(exchangeId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "EXCHANGE_NOT_FOUND", "Exchange not found"));
    }

    private Exchange requireExchangeForUpdate(UUID exchangeId) {
        return exchangeRepository
                .findByIdForUpdate(exchangeId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "EXCHANGE_NOT_FOUND", "Exchange not found"));
    }

    private void requireParticipant(Exchange exchange, UUID userId) {
        if (!exchange.getRequester().getId().equals(userId) && !exchange.getReceiver().getId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_A_PARTICIPANT", "Not a participant of this exchange");
        }
    }

    private void requireReceiver(Exchange exchange, UUID userId) {
        if (!exchange.getReceiver().getId().equals(userId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, "ONLY_RECEIVER_ALLOWED", "Only the receiver can do this");
        }
    }

    private void requireStatus(Exchange exchange, ExchangeStatus... allowed) {
        if (List.of(allowed).stream().noneMatch(status -> status == exchange.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "INVALID_STATE_TRANSITION",
                    "Exchange is " + exchange.getStatus() + ", expected one of " + List.of(allowed));
        }
    }
}
