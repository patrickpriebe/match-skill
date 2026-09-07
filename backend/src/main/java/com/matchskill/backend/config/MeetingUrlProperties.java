package com.matchskill.backend.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration, not code, so hosts are added without a release. */
@ConfigurationProperties(prefix = "app.meeting-url")
public record MeetingUrlProperties(List<String> allowedHosts) {}
