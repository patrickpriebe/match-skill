package com.matchskill.backend.exception;

import org.springframework.http.HttpStatus;

/** Carries the HTTP status plus the stable code/message pair returned as {@link com.matchskill.backend.dto.error.ApiError}. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
