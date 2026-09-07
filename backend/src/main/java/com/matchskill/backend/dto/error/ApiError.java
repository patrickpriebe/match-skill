package com.matchskill.backend.dto.error;

/** Stable shape for every error response: a machine code and a human message. */
public record ApiError(String code, String message) {}
