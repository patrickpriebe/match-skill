package com.matchskill.backend.service;

import com.matchskill.backend.config.MeetingUrlProperties;
import com.matchskill.backend.exception.ApiException;
import com.matchskill.backend.validation.MeetingUrlConstraints;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * A free URL field is a channel to a stranger, and an unvalidated link is a
 * phishing vector — checked against an allowlist of meeting-tool hosts, https
 * required.
 */
@Component
public class MeetingUrlValidator {

    private final MeetingUrlProperties properties;

    public MeetingUrlValidator(MeetingUrlProperties properties) {
        this.properties = properties;
    }

    public void validate(String meetingUrl) {
        if (meetingUrl == null || meetingUrl.isBlank()) {
            throw invalid();
        }
        if (meetingUrl.length() > MeetingUrlConstraints.MAX_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MEETING_URL",
                    "meetingUrl must not exceed " + MeetingUrlConstraints.MAX_LENGTH + " characters");
        }
        URI uri;
        try {
            uri = new URI(meetingUrl);
        } catch (URISyntaxException e) {
            throw invalid();
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw invalid();
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        boolean allowed =
                properties.allowedHosts().stream()
                        .map(allowedHost -> allowedHost.toLowerCase(Locale.ROOT))
                        .anyMatch(allowedHost -> host.equals(allowedHost) || host.endsWith("." + allowedHost));
        if (!allowed) {
            throw invalid();
        }
    }

    private ApiException invalid() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_MEETING_URL",
                "meetingUrl must be an https link to an allowed meeting host");
    }
}
