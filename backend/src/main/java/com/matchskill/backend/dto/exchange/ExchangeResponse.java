package com.matchskill.backend.dto.exchange;

import com.matchskill.backend.dto.skill.SkillResponse;
import com.matchskill.backend.entity.Exchange;
import com.matchskill.backend.entity.ExchangeStatus;
import com.matchskill.backend.entity.ExchangeStrength;
import java.time.Instant;
import java.util.UUID;

public record ExchangeResponse(
        UUID id,
        UUID requesterId,
        UUID receiverId,
        SkillResponse skillFromReceiver,
        SkillResponse skillFromRequester,
        ExchangeStrength strength,
        ExchangeStatus status,
        Instant scheduledAt,
        String meetingUrl,
        Instant createdAt,
        Instant updatedAt) {

    public static ExchangeResponse from(Exchange exchange) {
        return new ExchangeResponse(
                exchange.getId(),
                exchange.getRequester().getId(),
                exchange.getReceiver().getId(),
                SkillResponse.from(exchange.getSkillFromReceiver()),
                exchange.getSkillFromRequester() == null
                        ? null
                        : SkillResponse.from(exchange.getSkillFromRequester()),
                exchange.getStrength(),
                exchange.getStatus(),
                exchange.getScheduledAt(),
                exchange.getMeetingUrl(),
                exchange.getCreatedAt(),
                exchange.getUpdatedAt());
    }
}
