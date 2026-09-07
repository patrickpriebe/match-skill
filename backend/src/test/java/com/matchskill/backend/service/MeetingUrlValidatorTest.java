package com.matchskill.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.matchskill.backend.config.MeetingUrlProperties;
import com.matchskill.backend.exception.ApiException;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class MeetingUrlValidatorTest {

    private MeetingUrlValidator validator;

    @BeforeEach
    void setUp() {
        MeetingUrlProperties properties = new MeetingUrlProperties(List.of(
                "zoom.us",
                "meet.google.com",
                "teams.microsoft.com",
                "whereby.com"));
        validator = new MeetingUrlValidator(properties);
    }

    @ParameterizedTest(name = "Valid meeting URL: {0}")
    @ValueSource(strings = {
        "https://meet.google.com/abc-defg-hij",
        "https://zoom.us/j/1234567890",
        "https://us04web.zoom.us/j/987654321",
        "https://teams.microsoft.com/l/meetup-join/abc",
        "https://whereby.com/my-room",
        "HTTPS://MEET.GOOGLE.COM/XYZ"
    })
    void shouldAcceptAllowedHttpsMeetingUrls(String validUrl) {
        assertThatCode(() -> validator.validate(validUrl))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "Disallowed scheme: {0}")
    @ValueSource(strings = {
        "http://meet.google.com/abc-defg-hij",
        "ftp://zoom.us/meeting",
        "javascript:alert(1)",
        "file:///etc/passwd",
        "data:text/html,<script>alert(1)</script>"
    })
    void shouldRejectNonHttpsSchemes(String insecureUrl) {
        assertThatThrownBy(() -> validator.validate(insecureUrl))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    org.assertj.core.api.Assertions.assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    org.assertj.core.api.Assertions.assertThat(apiEx.getCode()).isEqualTo("INVALID_MEETING_URL");
                });
    }

    @ParameterizedTest(name = "Disallowed / spoofed host: {0}")
    @ValueSource(strings = {
        "https://evil-zoom.us/j/123",
        "https://zoom.us.attacker.com/j/123",
        "https://meet.google.com.phishing.org/room",
        "https://unknown-platform.io/meet",
        "https://discord.gg/invite",
        "https://localhost:8080/call"
    })
    void shouldRejectDisallowedHosts(String unallowedHostUrl) {
        assertThatThrownBy(() -> validator.validate(unallowedHostUrl))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("meetingUrl must be an https link to an allowed meeting host");
    }

    @ParameterizedTest(name = "Malformed or empty URL: \"{0}\"")
    @ValueSource(strings = {
        "",
        "   ",
        "not-a-valid-url",
        "https://",
        "https:///path"
    })
    void shouldRejectMalformedOrEmptyUrls(String invalidUrl) {
        assertThatThrownBy(() -> validator.validate(invalidUrl))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    org.assertj.core.api.Assertions.assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    @DisplayName("Null URL returns the same domain error as other invalid meeting URLs")
    void shouldThrowOnNullUrl() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiException = (ApiException) ex;
                    assertThat(apiException.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiException.getCode()).isEqualTo("INVALID_MEETING_URL");
                });
    }

    @ParameterizedTest(name = "Allowed meeting URL with {0} characters")
    @ValueSource(ints = {255, 256, 2048})
    void shouldAcceptUrlsThroughTheStorageBoundary(int length) {
        String prefix = "https://teams.microsoft.com/l/meetup-join/";
        String url = prefix + "a".repeat(length - prefix.length());
        assertThat(url).hasSize(length);
        assertThatCode(() -> validator.validate(url)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectAnAllowedHostUrlBeyondTheStorageBoundary() {
        String prefix = "https://teams.microsoft.com/l/meetup-join/";
        String url = prefix + "a".repeat(2049 - prefix.length());
        assertThatThrownBy(() -> validator.validate(url))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiException = (ApiException) ex;
                    assertThat(apiException.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiException.getCode()).isEqualTo("INVALID_MEETING_URL");
                });
    }

    @Test
    @ResourceLock(Resources.LOCALE)
    void shouldFoldHostCaseIndependentlyOfTheDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThatCode(() -> validator.validate("HTTPS://TEAMS.MICROSOFT.COM/l/meetup-join/123"))
                    .doesNotThrowAnyException();
            MeetingUrlValidator uppercaseAllowlist = new MeetingUrlValidator(
                    new MeetingUrlProperties(List.of("TEAMS.MICROSOFT.COM")));
            assertThatCode(() -> uppercaseAllowlist.validate("https://teams.microsoft.com/l/meetup-join/123"))
                    .doesNotThrowAnyException();
        } finally {
            Locale.setDefault(previous);
        }
    }
}
