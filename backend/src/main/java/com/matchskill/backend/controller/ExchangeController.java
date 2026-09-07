package com.matchskill.backend.controller;

import com.matchskill.backend.dto.common.PageResponse;
import com.matchskill.backend.dto.exchange.AcceptExchangeRequest;
import com.matchskill.backend.dto.exchange.CreateExchangeRequest;
import com.matchskill.backend.dto.exchange.ExchangeResponse;
import com.matchskill.backend.dto.exchange.ScheduleExchangeRequest;
import com.matchskill.backend.dto.feedback.CreateFeedbackRequest;
import com.matchskill.backend.dto.feedback.FeedbackResponse;
import com.matchskill.backend.dto.feedback.ExchangeFeedbackResponse;
import com.matchskill.backend.entity.ExchangeStatus;
import com.matchskill.backend.security.CurrentUser;
import com.matchskill.backend.service.ExchangeService;
import com.matchskill.backend.service.FeedbackService;
import com.matchskill.backend.util.Pagination;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/exchanges")
public class ExchangeController {

    private final ExchangeService exchangeService;
    private final FeedbackService feedbackService;

    public ExchangeController(ExchangeService exchangeService, FeedbackService feedbackService) {
        this.exchangeService = exchangeService;
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public ResponseEntity<ExchangeResponse> create(
            Authentication authentication, @Valid @RequestBody CreateExchangeRequest request) {
        var exchange =
                exchangeService.create(
                        CurrentUser.id(authentication), request.receiverId(), request.skillFromReceiver());
        return ResponseEntity.status(HttpStatus.CREATED).body(exchange);
    }

    @GetMapping
    public PageResponse<ExchangeResponse> list(
            Authentication authentication,
            @RequestParam(required = false) ExchangeStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(
                exchangeService.list(CurrentUser.id(authentication), status, Pagination.pageRequest(page, size)));
    }

    @GetMapping("/{id}")
    public ExchangeResponse get(Authentication authentication, @PathVariable UUID id) {
        return exchangeService.get(CurrentUser.id(authentication), id);
    }

    @PostMapping("/{id}/accept")
    public ExchangeResponse accept(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody AcceptExchangeRequest request) {
        return exchangeService.accept(CurrentUser.id(authentication), id, request.skillFromRequester());
    }

    @PostMapping("/{id}/decline")
    public ExchangeResponse decline(Authentication authentication, @PathVariable UUID id) {
        return exchangeService.decline(CurrentUser.id(authentication), id);
    }

    @PostMapping("/{id}/schedule")
    public ExchangeResponse schedule(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody ScheduleExchangeRequest request) {
        return exchangeService.schedule(
                CurrentUser.id(authentication), id, request.scheduledAt(), request.meetingUrl());
    }

    @PostMapping("/{id}/complete")
    public ExchangeResponse complete(Authentication authentication, @PathVariable UUID id) {
        return exchangeService.complete(CurrentUser.id(authentication), id);
    }

    @PostMapping("/{id}/cancel")
    public ExchangeResponse cancel(Authentication authentication, @PathVariable UUID id) {
        return exchangeService.cancel(CurrentUser.id(authentication), id);
    }

    @PostMapping("/{id}/feedback")
    public ResponseEntity<FeedbackResponse> submitFeedback(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody CreateFeedbackRequest request) {
        var feedback =
                feedbackService.create(CurrentUser.id(authentication), id, request.rating(), request.comment());
        return ResponseEntity.status(HttpStatus.CREATED).body(feedback);
    }

    @GetMapping("/{id}/feedback")
    public ExchangeFeedbackResponse feedback(Authentication authentication, @PathVariable UUID id) {
        return feedbackService.getForExchange(CurrentUser.id(authentication), id);
    }
}
