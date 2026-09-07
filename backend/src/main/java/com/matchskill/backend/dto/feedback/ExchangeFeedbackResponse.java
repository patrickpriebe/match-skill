package com.matchskill.backend.dto.feedback;

public record ExchangeFeedbackResponse(
        FeedbackResponse mine, FeedbackResponse theirs, boolean counterpartSubmitted) {}
