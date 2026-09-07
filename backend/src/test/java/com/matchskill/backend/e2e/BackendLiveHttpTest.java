package com.matchskill.backend.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matchskill.backend.entity.Skill;
import com.matchskill.backend.entity.SkillStatus;
import com.matchskill.backend.repository.SkillRepository;
import com.matchskill.backend.service.RateLimiterService;
import com.matchskill.backend.util.Slugs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import com.matchskill.backend.BackendApplication;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SpringBootTest(
        classes = BackendApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:live-http-test;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.security.oauth2.client.registration.google.client-id=",
                "spring.security.oauth2.client.registration.google.client-secret="
        }
)
class BackendLiveHttpTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SkillRepository skillRepository;

    @MockBean
    private RateLimiterService rateLimiterService;

    private HttpClient httpClient;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        when(rateLimiterService.tryConsume(anyString(), anyInt(), anyInt())).thenReturn(true);
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        baseUrl = "http://localhost:" + port + "/api";
    }

    record HttpResponseWrapper(int statusCode, String body, JsonNode json) {}

    private HttpResponseWrapper send(String method, String path, String token, Object payload) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(10));

        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }

        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.noBody();
        if (payload != null) {
            String jsonStr = payload instanceof String ? (String) payload : objectMapper.writeValueAsString(payload);
            bodyPublisher = HttpRequest.BodyPublishers.ofString(jsonStr);
            builder.header("Content-Type", "application/json");
        }

        builder.method(method, bodyPublisher);
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

        JsonNode jsonNode = null;
        if (response.body() != null && !response.body().isBlank() && response.body().trim().startsWith("{") || response.body().trim().startsWith("[")) {
            try {
                jsonNode = objectMapper.readTree(response.body());
            } catch (Exception ignored) {}
        }
        return new HttpResponseWrapper(response.statusCode(), response.body(), jsonNode);
    }

    private String seedSkill(String name, SkillStatus status) {
        String slug = Slugs.slugify(name);
        return skillRepository.findBySlug(slug)
                .map(s -> s.getId().toString())
                .orElseGet(() -> skillRepository.save(Skill.builder()
                        .name(name)
                        .slug(slug)
                        .status(status)
                        .build()).getId().toString());
    }

    @Test
    @DisplayName("Live HTTP Socket: Traverses full user lifecycle, F1 atomic preferences, exchange state machine, and F2 double-blind feedback")
    void testLiveHttpEndToEndJourney() throws Exception {
        // 1. Seed Skills (Java, React approved; Blockchain pending)
        String javaId = seedSkill("Java", SkillStatus.APPROVED);
        String reactId = seedSkill("React", SkillStatus.APPROVED);
        String pendingSkillId = seedSkill("Blockchain", SkillStatus.PENDING_REVIEW);

        // 2. Register User A (Requester) and User B (Receiver)
        String emailA = "alice." + UUID.randomUUID() + "@example.com";
        var resRegA = send("POST", "/auth/register", null, Map.of(
                "email", emailA,
                "password", "Password123!",
                "displayName", "Alice Requester",
                "timeZone", "America/Sao_Paulo"
        ));
        assertThat(resRegA.statusCode()).isEqualTo(201);
        String tokenA = resRegA.json().get("token").asText();

        String emailB = "bob." + UUID.randomUUID() + "@example.com";
        var resRegB = send("POST", "/auth/register", null, Map.of(
                "email", emailB,
                "password", "Password123!",
                "displayName", "Bob Receiver",
                "timeZone", "Europe/London"
        ));
        assertThat(resRegB.statusCode()).isEqualTo(201);
        String tokenB = resRegB.json().get("token").asText();

        // Check /auth/me for User A
        var resMeA = send("GET", "/auth/me", tokenA, null);
        assertThat(resMeA.statusCode()).isEqualTo(200);
        String userAId = resMeA.json().get("id").asText();
        assertThat(resMeA.json().get("timeZone").asText()).isEqualTo("America/Sao_Paulo");

        var resMeB = send("GET", "/auth/me", tokenB, null);
        assertThat(resMeB.statusCode()).isEqualTo(200);
        String userBId = resMeB.json().get("id").asText();

        // 3. Rejection of PENDING_REVIEW skill in /me/skills
        var resPendingFail = send("PUT", "/me/skills", tokenA, Map.of(
                "offeredSkillIds", List.of(pendingSkillId),
                "wantedSkillIds", List.of(reactId)
        ));
        assertThat(resPendingFail.statusCode()).isEqualTo(400);
        assertThat(resPendingFail.json().get("code").asText()).isEqualTo("SKILL_NOT_APPROVED");

        // 4. Register Valid Skills (User A: Offers React, Wants Java; User B: Offers Java, Wants React -> MUTUAL match)
        var resSkillsA = send("PUT", "/me/skills", tokenA, Map.of(
                "offeredSkillIds", List.of(reactId),
                "wantedSkillIds", List.of(javaId)
        ));
        assertThat(resSkillsA.statusCode()).isEqualTo(200);

        var resSkillsB = send("PUT", "/me/skills", tokenB, Map.of(
                "offeredSkillIds", List.of(javaId),
                "wantedSkillIds", List.of(reactId)
        ));
        assertThat(resSkillsB.statusCode()).isEqualTo(200);

        // 5. F1: Availability & Timezone Atomic Persistence
        // 5a. Validation error on invalid timezone -> rollback
        var resF1Fail = send("PUT", "/me/availability", tokenA, Map.of(
                "timeZone", "Invalid/Zone_Name",
                "windows", List.of(Map.of("dayOfWeek", "MONDAY", "startTime", "09:00", "endTime", "12:00"))
        ));
        assertThat(resF1Fail.statusCode()).isEqualTo(400);
        // Ensure user timezone was NOT updated
        var resCheckZoneA = send("GET", "/auth/me", tokenA, null);
        assertThat(resCheckZoneA.json().get("timeZone").asText()).isEqualTo("America/Sao_Paulo");

        // 5b. Valid atomic update with windows and new timezone
        var resF1Success = send("PUT", "/me/availability", tokenA, Map.of(
                "timeZone", "America/New_York",
                "windows", List.of(
                        Map.of("dayOfWeek", "MONDAY", "startTime", "09:00", "endTime", "12:00"),
                        Map.of("dayOfWeek", "WEDNESDAY", "startTime", "14:00", "endTime", "17:00")
                )
        ));
        assertThat(resF1Success.statusCode()).isEqualTo(200);
        assertThat(resF1Success.json().size()).isEqualTo(2);

        // Verify timezone updated on /auth/me
        resCheckZoneA = send("GET", "/auth/me", tokenA, null);
        assertThat(resCheckZoneA.json().get("timeZone").asText()).isEqualTo("America/New_York");

        // Setup availability for User B
        send("PUT", "/me/availability", tokenB, Map.of(
                "timeZone", "Europe/London",
                "windows", List.of(
                        Map.of("dayOfWeek", "MONDAY", "startTime", "14:00", "endTime", "17:00")
                )
        ));

        // 6. Matching verification
        var resMatchesA = send("GET", "/matches", tokenA, null);
        assertThat(resMatchesA.statusCode()).isEqualTo(200);
        assertThat(resMatchesA.json().get("items").size()).isGreaterThanOrEqualTo(1);
        var matchUserB = resMatchesA.json().get("items").get(0);
        assertThat(matchUserB.get("userId").asText()).isEqualTo(userBId);
        assertThat(matchUserB.get("strength").asText()).isEqualTo("MUTUAL");

        // 7. Exchange Lifecycle: User A creates exchange to User B requesting Java
        var resCreateEx = send("POST", "/exchanges", tokenA, Map.of(
                "receiverId", userBId,
                "skillFromReceiver", javaId
        ));
        assertThat(resCreateEx.statusCode()).isEqualTo(201);
        String exchangeId = resCreateEx.json().get("id").asText();
        assertThat(resCreateEx.json().get("status").asText()).isEqualTo("REQUESTED");

        // 7a. Non-completed exchange feedback guard (F2 exact error: 409 EXCHANGE_NOT_COMPLETED)
        var resEarlyFb = send("GET", "/exchanges/" + exchangeId + "/feedback", tokenA, null);
        assertThat(resEarlyFb.statusCode()).isEqualTo(409);
        assertThat(resEarlyFb.json().get("code").asText()).isEqualTo("EXCHANGE_NOT_COMPLETED");

        // 7b. Accept Exchange (User B accepts and selects React from User A)
        var resAccept = send("POST", "/exchanges/" + exchangeId + "/accept", tokenB, Map.of(
                "skillFromRequester", reactId
        ));
        assertThat(resAccept.statusCode()).isEqualTo(200);
        assertThat(resAccept.json().get("status").asText()).isEqualTo("ACCEPTED");

        // 7c. Schedule Exchange
        Instant futureMeeting = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        var resSchedule = send("POST", "/exchanges/" + exchangeId + "/schedule", tokenA, Map.of(
                "scheduledAt", futureMeeting.toString(),
                "meetingUrl", "https://meet.google.com/test-room-abc"
        ));
        assertThat(resSchedule.statusCode()).isEqualTo(200);
        assertThat(resSchedule.json().get("status").asText()).isEqualTo("SCHEDULED");

        // 7d. Complete Exchange
        var resComplete = send("POST", "/exchanges/" + exchangeId + "/complete", tokenB, null);
        assertThat(resComplete.statusCode()).isEqualTo(200);
        assertThat(resComplete.json().get("status").asText()).isEqualTo("COMPLETED");

        // 8. F2: Server-Enforced Double-Blind Feedback & Publication Policy
        // 8a. Phase 0: Neither submitted
        var resFbPhase0 = send("GET", "/exchanges/" + exchangeId + "/feedback", tokenA, null);
        assertThat(resFbPhase0.statusCode()).isEqualTo(200);
        assertThat(resFbPhase0.json().get("mine").isNull()).isTrue();
        assertThat(resFbPhase0.json().get("theirs").isNull()).isTrue();
        assertThat(resFbPhase0.json().get("counterpartSubmitted").asBoolean()).isFalse();

        // 8b. Third party access forbidden (403 NOT_A_PARTICIPANT)
        String emailC = "outsider." + UUID.randomUUID() + "@example.com";
        var resRegC = send("POST", "/auth/register", null, Map.of(
                "email", emailC, "password", "Password123!", "displayName", "Charlie ThirdParty", "timeZone", "UTC"
        ));
        assertThat(resRegC.statusCode()).isEqualTo(201);
        String tokenC = resRegC.json().get("token").asText();
        var resThirdPartyFb = send("GET", "/exchanges/" + exchangeId + "/feedback", tokenC, null);
        assertThat(resThirdPartyFb.statusCode()).isEqualTo(403);
        assertThat(resThirdPartyFb.json().get("code").asText()).isEqualTo("NOT_A_PARTICIPANT");

        // 8c. User A submits feedback (Phase 1)
        var resSubmitA = send("POST", "/exchanges/" + exchangeId + "/feedback", tokenA, Map.of(
                "rating", 5,
                "comment", "Superb Java explanations!"
        ));
        assertThat(resSubmitA.statusCode()).isEqualTo(201);

        // User A reads: mine present, theirs null, counterpartSubmitted false
        var resFbPhase1A = send("GET", "/exchanges/" + exchangeId + "/feedback", tokenA, null);
        assertThat(resFbPhase1A.statusCode()).isEqualTo(200);
        assertThat(resFbPhase1A.json().get("mine").get("rating").asInt()).isEqualTo(5);
        assertThat(resFbPhase1A.json().get("theirs").isNull()).isTrue();
        assertThat(resFbPhase1A.json().get("counterpartSubmitted").asBoolean()).isFalse();

        // Phase 1B: User B reads: mine null, theirs STRICTLY NULL (blinded!), counterpartSubmitted TRUE
        var resFbPhase1B = send("GET", "/exchanges/" + exchangeId + "/feedback", tokenB, null);
        assertThat(resFbPhase1B.statusCode()).isEqualTo(200);
        assertThat(resFbPhase1B.json().get("mine").isNull()).isTrue();
        assertThat(resFbPhase1B.json().get("theirs").isNull()).isTrue(); // Blindness invariant!
        assertThat(resFbPhase1B.json().get("counterpartSubmitted").asBoolean()).isTrue();

        // Privacy check on User B profile: User A rated User B, so User B is the recipient.
        // User B checking their own received reviews must NOT see User A's unrequited review yet!
        var resProfileFbB = send("GET", "/users/" + userBId + "/feedback", tokenB, null);
        assertThat(resProfileFbB.statusCode()).isEqualTo(200);
        assertThat(resProfileFbB.json().size()).isEqualTo(0);

        // Third party Charlie checking User B's profile must see 0 reviews and unchanged reputation
        var resProfileFbC = send("GET", "/users/" + userBId + "/feedback", tokenC, null);
        assertThat(resProfileFbC.statusCode()).isEqualTo(200);
        assertThat(resProfileFbC.json().size()).isEqualTo(0);

        var resProfileB = send("GET", "/users/" + userBId, tokenC, null);
        assertThat(resProfileB.statusCode()).isEqualTo(200);
        assertThat(resProfileB.json().get("reputationCount").asInt()).isEqualTo(0);

        // 8d. User B submits feedback (Phase 2 - Dual Publication)
        var resSubmitB = send("POST", "/exchanges/" + exchangeId + "/feedback", tokenB, Map.of(
                "rating", 4,
                "comment", "Great React mentoring."
        ));
        assertThat(resSubmitB.statusCode()).isEqualTo(201);

        // Duplicate submission rejected (409 FEEDBACK_ALREADY_SUBMITTED)
        var resDupB = send("POST", "/exchanges/" + exchangeId + "/feedback", tokenB, Map.of(
                "rating", 4, "comment", "Duplicate attempt"
        ));
        assertThat(resDupB.statusCode()).isEqualTo(409);
        assertThat(resDupB.json().get("code").asText()).isEqualTo("FEEDBACK_ALREADY_SUBMITTED");

        // Both participants now see both reviews
        var resFbPhase2A = send("GET", "/exchanges/" + exchangeId + "/feedback", tokenA, null);
        assertThat(resFbPhase2A.statusCode()).isEqualTo(200);
        assertThat(resFbPhase2A.json().get("mine").get("rating").asInt()).isEqualTo(5);
        assertThat(resFbPhase2A.json().get("theirs").get("rating").asInt()).isEqualTo(4);

        var resFbPhase2B = send("GET", "/exchanges/" + exchangeId + "/feedback", tokenB, null);
        assertThat(resFbPhase2B.statusCode()).isEqualTo(200);
        assertThat(resFbPhase2B.json().get("mine").get("rating").asInt()).isEqualTo(4);
        assertThat(resFbPhase2B.json().get("theirs").get("rating").asInt()).isEqualTo(5);

        // Public profile verification: Both received feedback lists now published!
        // User B received User A's review (rating 5)
        var resPublishedB = send("GET", "/users/" + userBId + "/feedback", tokenC, null);
        assertThat(resPublishedB.statusCode()).isEqualTo(200);
        assertThat(resPublishedB.json().size()).isEqualTo(1);
        assertThat(resPublishedB.json().get(0).get("rating").asInt()).isEqualTo(5);

        // User A received User B's review (rating 4)
        var resPublishedA = send("GET", "/users/" + userAId + "/feedback", tokenC, null);
        assertThat(resPublishedA.statusCode()).isEqualTo(200);
        assertThat(resPublishedA.json().size()).isEqualTo(1);
        assertThat(resPublishedA.json().get(0).get("rating").asInt()).isEqualTo(4);

        // Reputation aggregates updated
        resProfileB = send("GET", "/users/" + userBId, tokenC, null);
        assertThat(resProfileB.json().get("reputationCount").asInt()).isEqualTo(1);
        assertThat(resProfileB.json().get("reputationAverage").asDouble()).isEqualTo(5.0);

        var resProfileA = send("GET", "/users/" + userAId, tokenC, null);
        assertThat(resProfileA.json().get("reputationCount").asInt()).isEqualTo(1);
        assertThat(resProfileA.json().get("reputationAverage").asDouble()).isEqualTo(4.0);
    }
}
