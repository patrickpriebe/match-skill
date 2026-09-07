package com.matchskill.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matchskill.backend.entity.Skill;
import com.matchskill.backend.entity.SkillStatus;
import com.matchskill.backend.repository.SkillRepository;
import com.matchskill.backend.service.RateLimiterService;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Real application, security, controllers and JPA; only rate-limit storage is mocked. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:backend-api;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.security.oauth2.client.registration.google.client-id=",
        "spring.security.oauth2.client.registration.google.client-secret="
})
@AutoConfigureMockMvc
class BackendApiIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private SkillRepository skills;
    @MockBean private RateLimiterService limiter;

    @BeforeEach
    void allowRateLimitedRoutes() {
        when(limiter.tryConsume(anyString(), anyInt(), anyInt())).thenReturn(true);
    }

    @Test
    void authenticatedExchangeFlowPreservesContractsAndPublishesFeedbackTogether() throws Exception {
        var requester = register();
        var receiver = register();
        var outsider = register();
        var java = skill();
        var react = skill();
        call("PUT", "/me/skills", requester.token(), Map.of(
                "offeredSkillIds", List.of(java), "wantedSkillIds", List.of(react)), 200);
        call("PUT", "/me/skills", receiver.token(), Map.of(
                "offeredSkillIds", List.of(react), "wantedSkillIds", List.of(java)), 200);

        var windows = List.of(Map.of("dayOfWeek", "MONDAY", "startTime", "09:00", "endTime", "10:00"));
        var saved = call("PUT", "/me/availability", requester.token(),
                Map.of("windows", windows, "timeZone", "America/New_York"), 200);
        assertThat(saved.isArray()).isTrue();
        assertThat(saved.get(0).get("startTime").asText()).isEqualTo("09:00:00");
        assertThat(call("GET", "/auth/me", requester.token(), null, 200).get("timeZone").asText())
                .isEqualTo("America/New_York");
        assertThat(profile(requester.id(), outsider.token()).get("timeZone").asText()).isEqualTo("America/New_York");
        call("PUT", "/me/availability", requester.token(), Map.of("windows", List.of(), "timeZone", "bad-zone"), 400);
        assertThat(call("GET", "/me/availability", requester.token(), null, 200)).isEqualTo(saved);

        var created = call("POST", "/exchanges", requester.token(),
                Map.of("receiverId", receiver.id(), "skillFromReceiver", react), 201);
        String exchangePath = "/exchanges/" + created.get("id").asText();
        assertThat(created.get("createdAt").isTextual()).isTrue();
        assertThat(call("GET", exchangePath, requester.token(), null, 200).get("skillFromReceiver").get("id").asText())
                .isEqualTo(react);
        call("POST", exchangePath + "/accept", receiver.token(), Map.of("skillFromRequester", java), 200);
        call("POST", exchangePath + "/schedule", requester.token(), Map.of(
                "scheduledAt", Instant.now().plus(1, ChronoUnit.DAYS).toString(),
                "meetingUrl", "https://meet.google.com/abc-defg-hij"), 200);
        call("POST", exchangePath + "/complete", requester.token(), null, 200);

        var submitted = call("POST", exchangePath + "/feedback", requester.token(),
                Map.of("rating", 5, "comment", "Private until both submit"), 201);
        assertThat(submitted.get("createdAt").isTextual()).isTrue();
        var receiverState = call("GET", exchangePath + "/feedback", receiver.token(), null, 200);
        assertThat(receiverState.get("mine").isNull()).isTrue();
        assertThat(receiverState.get("theirs").isNull()).isTrue();
        assertThat(receiverState.get("counterpartSubmitted").asBoolean()).isTrue();
        assertThat(call("GET", exchangePath + "/feedback", outsider.token(), null, 403).get("code").asText())
                .isEqualTo("NOT_A_PARTICIPANT");
        assertThat(call("GET", "/users/" + receiver.id() + "/feedback", outsider.token(), null, 200)).isEmpty();
        assertThat(call("GET", "/users/" + receiver.id() + "/feedback", receiver.token(), null, 200)).isEmpty();
        assertThat(call("GET", "/users/" + receiver.id() + "/feedback", requester.token(), null, 200)).hasSize(1);
        assertThat(profile(receiver.id(), outsider.token()).get("reputationCount").asLong()).isZero();
        assertThat(call("GET", "/matches", requester.token(), null, 200)
                .get("items").get(0).get("reputationCount").asLong()).isZero();
        assertThat(call("GET", "/search?skill=" + react, requester.token(), null, 200)
                .get("items").get(0).get("reputationCount").asLong()).isZero();

        assertThat(call("POST", exchangePath + "/feedback", requester.token(), Map.of("rating", 1), 409)
                .get("code").asText()).isEqualTo("FEEDBACK_ALREADY_SUBMITTED");
        call("POST", exchangePath + "/feedback", receiver.token(), Map.of("rating", 4), 201);
        assertThat(call("GET", exchangePath + "/feedback", receiver.token(), null, 200)
                .get("theirs").get("rating").asInt()).isEqualTo(5);
        assertThat(call("GET", exchangePath + "/feedback", requester.token(), null, 200)
                .get("theirs").get("rating").asInt()).isEqualTo(4);
        assertThat(call("GET", "/users/" + receiver.id() + "/feedback", outsider.token(), null, 200)).hasSize(1);
        assertThat(profile(receiver.id(), outsider.token()).get("reputationAverage").asDouble()).isEqualTo(5.0);
        assertThat(profile(requester.id(), outsider.token()).get("reputationAverage").asDouble()).isEqualTo(4.0);
        assertThat(call("GET", "/matches", requester.token(), null, 200)
                .get("items").get(0).get("reputationAverage").asDouble()).isEqualTo(5.0);
        assertThat(call("GET", "/search?skill=" + react, requester.token(), null, 200)
                .get("items").get(0).get("reputationAverage").asDouble()).isEqualTo(5.0);
    }

    private JsonNode profile(String userId, String token) throws Exception {
        return call("GET", "/users/" + userId, token, null, 200);
    }

    @Test
    void skillSuggestionsDistinguishSymbolsAndKeepIdentityKeyPrivate() throws Exception {
        Account account = register();
        var ids = new java.util.HashSet<String>();
        for (String name : List.of("C", "C++", "C#")) {
            JsonNode response = call("POST", "/skills/suggest", account.token(), Map.of("name", name), 201);
            assertThat(response.size()).isEqualTo(4);
            assertThat(response.has("identityKey")).isFalse();
            ids.add(response.get("id").asText());
            assertThat(call("POST", "/skills/suggest", account.token(),
                    Map.of("name", name.toLowerCase(java.util.Locale.ROOT)), 201).get("id"))
                    .isEqualTo(response.get("id"));
        }
        assertThat(ids).hasSize(3);
        assertThat(call("POST", "/skills/suggest", account.token(), Map.of("name", "+#!"), 400)
                .get("code").asText()).isEqualTo("INVALID_SKILL_NAME");
    }

    private Account register() throws Exception {
        var response = call("POST", "/auth/register", null, Map.of(
                "email", UUID.randomUUID() + "@example.test", "password", "ValidPassword!123",
                "displayName", "Test person", "timeZone", "UTC"), 201);
        assertThat(response.size()).isEqualTo(1);
        String token = response.get("token").asText();
        return new Account(call("GET", "/auth/me", token, null, 200).get("id").asText(), token);
    }

    private String skill() {
        String name = UUID.randomUUID().toString();
        return skills.saveAndFlush(Skill.builder().name(name).slug(name).status(SkillStatus.APPROVED).build())
                .getId().toString();
    }

    private JsonNode call(String method, String path, String token, Object body, int expected) throws Exception {
        var request = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), URI.create("/api" + path))
                .contextPath("/api").servletPath(path.split("\\?", 2)[0]);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        String response = mvc.perform(request).andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private record Account(String id, String token) {}
}
