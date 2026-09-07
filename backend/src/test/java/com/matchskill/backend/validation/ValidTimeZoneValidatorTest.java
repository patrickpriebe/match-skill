package com.matchskill.backend.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ValidTimeZoneValidatorTest {

    private ValidTimeZoneValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ValidTimeZoneValidator();
    }

    @ParameterizedTest(name = "Valid timezone: {0}")
    @ValueSource(strings = {
        "UTC",
        "America/Sao_Paulo",
        "Europe/London",
        "America/New_York",
        "Asia/Tokyo",
        "GMT"
    })
    void shouldAcceptValidIanaTimeZones(String timeZone) {
        assertThat(validator.isValid(timeZone, null)).isTrue();
    }

    @ParameterizedTest(name = "Invalid timezone: {0}")
    @ValueSource(strings = {
        "Invalid/Timezone",
        "Mars/Olympus",
        "GMT+99",
        "random-string",
        "America/NonExistentCity"
    })
    void shouldRejectInvalidTimeZones(String timeZone) {
        assertThat(validator.isValid(timeZone, null)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    @DisplayName("Null or blank string returns true (delegates to @NotBlank)")
    void shouldReturnTrueForNullOrBlank(String value) {
        assertThat(validator.isValid(value, null)).isTrue();
    }
}
