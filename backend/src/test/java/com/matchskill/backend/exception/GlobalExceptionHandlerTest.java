package com.matchskill.backend.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.matchskill.backend.dto.error.ApiError;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("Regression P0: DataIntegrityViolationException returns 409 with DATA_INTEGRITY_VIOLATION code")
    void shouldHandleDataIntegrityViolation() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("Unique index or primary key violation");

        ResponseEntity<ApiError> response = handler.handleDataIntegrityViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("DATA_INTEGRITY_VIOLATION");
        assertThat(response.getBody().message()).isEqualTo("The request conflicts with existing data");
    }

    @Test
    @DisplayName("Regression P0: Unexpected generic Exception returns 500 with INTERNAL_ERROR code without leaking details")
    void shouldHandleUnexpectedException() {
        RuntimeException ex = new RuntimeException("Sensitive database credentials or internal stack trace");

        ResponseEntity<ApiError> response = handler.handleUnexpected(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).isEqualTo("Something went wrong");
        assertThat(response.getBody().message()).doesNotContain("Sensitive");
    }

    @Test
    @DisplayName("ApiException returns exact status and code from exception")
    void shouldHandleApiException() {
        ApiException ex = new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found");

        ResponseEntity<ApiError> response = handler.handleApiException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("USER_NOT_FOUND");
        assertThat(response.getBody().message()).isEqualTo("User not found");
    }

    @Test
    @DisplayName("MethodArgumentNotValidException returns 400 with VALIDATION_ERROR code")
    void shouldHandleValidationException() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("object", "timeZone", "must be a valid IANA time zone id");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<ApiError> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().message()).contains("timeZone must be a valid IANA time zone id");
    }
}
