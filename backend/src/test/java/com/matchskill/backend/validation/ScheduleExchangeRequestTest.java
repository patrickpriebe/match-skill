package com.matchskill.backend.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.matchskill.backend.dto.exchange.ScheduleExchangeRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ScheduleExchangeRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @ParameterizedTest(name = "Request accepts a {0}-character meeting URL")
    @ValueSource(ints = {255, 256, 2048})
    void acceptsUrlsThroughTheStorageBoundary(int length) {
        assertThat(validator.validate(new ScheduleExchangeRequest(Instant.now(), urlOfLength(length)))).isEmpty();
    }

    @Test
    void rejectsUrlAboveTheStorageBoundary() {
        var violations = validator.validate(new ScheduleExchangeRequest(Instant.now(), urlOfLength(2049)));
        assertThat(violations).extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("meetingUrl");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void keepsRejectingMissingOrBlankUrls(String url) {
        var violations = validator.validate(new ScheduleExchangeRequest(Instant.now(), url));
        assertThat(violations).extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("meetingUrl");
    }

    @Test
    void keepsRequiringTheScheduledInstant() {
        var violations = validator.validate(new ScheduleExchangeRequest(null, urlOfLength(256)));
        assertThat(violations).extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("scheduledAt");
    }

    private static String urlOfLength(int length) {
        String prefix = "https://teams.microsoft.com/l/meetup-join/";
        return prefix + "a".repeat(length - prefix.length());
    }
}
