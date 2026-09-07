package com.matchskill.backend.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matchskill.backend.BackendApplication;
import com.matchskill.backend.entity.Availability;
import com.matchskill.backend.entity.Feedback;
import com.matchskill.backend.entity.Skill;
import com.matchskill.backend.entity.SkillStatus;
import com.matchskill.backend.exception.ApiException;
import com.matchskill.backend.repository.AvailabilityRepository;
import com.matchskill.backend.repository.ExchangeRepository;
import com.matchskill.backend.repository.FeedbackRepository;
import com.matchskill.backend.repository.SkillRepository;
import com.matchskill.backend.service.RateLimiterService;
import com.matchskill.backend.util.Slugs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Live integration test executed against real PostgreSQL and real Redis.
 * Named *IT so it is excluded from default Maven Surefire runs (*Test.java)
 * and explicitly executed via -Dtest=PostgresRedisLiveIT.
 */
@SpringBootTest(
        classes = BackendApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
public class PostgresRedisLiveIT {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String dbHost = System.getProperty("test.db.host", System.getenv().getOrDefault("TEST_DB_HOST", "127.0.0.1"));
        String dbPort = System.getProperty("test.db.port", System.getenv().getOrDefault("TEST_DB_PORT", "15432"));
        String dbName = System.getProperty("test.db.name", System.getenv().getOrDefault("TEST_DB_NAME", "matchskill_validation"));
        String dbUrl = System.getProperty("test.db.url", System.getenv().getOrDefault("TEST_DB_URL", "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName));
        String dbUser = System.getProperty("test.db.user", System.getenv().getOrDefault("TEST_DB_USER", "matchskill_validation"));
        String dbPass = System.getProperty("test.db.pass", System.getenv().getOrDefault("TEST_DB_PASS", "local-validation-only"));

        String redisHost = System.getProperty("test.redis.host", System.getenv().getOrDefault("TEST_REDIS_HOST", "127.0.0.1"));
        int redisPort = Integer.parseInt(System.getProperty("test.redis.port", System.getenv().getOrDefault("TEST_REDIS_PORT", "16379")));

        registry.add("spring.datasource.url", () -> dbUrl);
        registry.add("spring.datasource.username", () -> dbUser);
        registry.add("spring.datasource.password", () -> dbPass);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        registry.add("spring.data.redis.host", () -> redisHost);
        registry.add("spring.data.redis.port", () -> redisPort);
        registry.add("spring.security.oauth2.client.registration.google.client-id", () -> "");
        registry.add("spring.security.oauth2.client.registration.google.client-secret", () -> "");
        registry.add("app.cors.allowed-origins", () -> "http://127.0.0.1:4176,http://localhost:4176,http://127.0.0.1:4174,http://localhost:4174,http://127.0.0.1:5173,http://localhost:5173,http://localhost:3000");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private AvailabilityRepository availabilityRepository;

    @Autowired
    private ExchangeRepository exchangeRepository;

    private HttpClient httpClient;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        assertThat(Mockito.mockingDetails(rateLimiterService).isMock())
                .as("RateLimiterService must be the real production Spring bean, not a mock")
                .isFalse();

        // Clear IP rate limit keys in Redis db0 so tests running from 127.0.0.1 start with a fresh quota
        redisTemplate.delete(List.of(
                "ratelimit:login:127.0.0.1",
                "ratelimit:register:127.0.0.1"
        ));

        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        baseUrl = "http://localhost:" + port + "/api";
    }

    private UUID ensureSkill(String name) {
        String slug = Slugs.slugify(name);
        return skillRepository.findBySlug(slug)
                .map(Skill::getId)
                .orElseGet(() -> skillRepository.save(Skill.builder()
                        .name(name)
                        .slug(slug)
                        .status(SkillStatus.APPROVED)
                        .build()).getId());
    }

    record HttpResponseWrapper(int statusCode, String body, JsonNode json, Map<String, List<String>> headers) {}

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
        if (response.body() != null && !response.body().isBlank() && (response.body().trim().startsWith("{") || response.body().trim().startsWith("["))) {
            try {
                jsonNode = objectMapper.readTree(response.body());
            } catch (Exception ignored) {}
        }
        return new HttpResponseWrapper(response.statusCode(), response.body(), jsonNode, response.headers().map());
    }

    @Test
    @DisplayName("Redis Lua Rate Limiting: 5 rapid login requests allowed, 6th returns 429 with Retry-After; real Redis key counters and TTL verified")
    void testRedisRateLimitingEnforcement() throws Exception {
        String testIpKey = "ratelimit:login:127.0.0.1";
        redisTemplate.delete(testIpKey);

        String testEmail = "ratelimit.test." + System.currentTimeMillis() + "@example.com";
        Map<String, String> badLogin = Map.of("email", testEmail, "password", "WrongPass123!");

        // 5 requests allowed (return 401 Unauthorized for bad credentials)
        for (int i = 1; i <= 5; i++) {
            HttpResponseWrapper res = send("POST", "/auth/login", null, badLogin);
            assertThat(res.statusCode())
                    .as("Request %d should be processed and rejected with 401", i)
                    .isEqualTo(401);

            String currentCount = redisTemplate.opsForValue().get(testIpKey);
            assertThat(currentCount)
                    .as("Redis counter must be updated by Lua script after request %d", i)
                    .isEqualTo(String.valueOf(i));

            Long ttl = redisTemplate.getExpire(testIpKey);
            assertThat(ttl)
                    .as("Redis key TTL must be set and positive")
                    .isNotNull()
                    .isGreaterThan(0)
                    .isLessThanOrEqualTo(60);
        }

        // 6th request triggers rate limit in Redis Lua
        HttpResponseWrapper limited = send("POST", "/auth/login", null, badLogin);
        assertThat(limited.statusCode())
                .as("6th request must be rate-limited with 429")
                .isEqualTo(429);
        assertThat(limited.json().path("code").asText())
                .isEqualTo("RATE_LIMITED");

        // Verify Redis counter after 6th attempt
        String countAfterBlocked = redisTemplate.opsForValue().get(testIpKey);
        assertThat(countAfterBlocked).isEqualTo("6");

        // Verify Retry-After header
        List<String> retryAfter = limited.headers().get("Retry-After");
        assertThat(retryAfter).isNotNull().isNotEmpty();
        assertThat(Integer.parseInt(retryAfter.getFirst())).isGreaterThan(0);
    }

    @Test
    @DisplayName("Meeting URL length: up to 2048 characters allowed and persisted in PostgreSQL; >2048 rejected with 400")
    void testMeetingUrlLengthSupport() throws Exception {
        long ts = System.currentTimeMillis();

        // 1. Register Alice and Bob
        HttpResponseWrapper aliceReg = send("POST", "/auth/register", null, Map.of(
                "email", "alice.url." + ts + "@example.com",
                "password", "Password123!",
                "displayName", "Alice URL",
                "timeZone", "America/Sao_Paulo"
        ));
        assertThat(aliceReg.statusCode()).isEqualTo(201);
        String aliceToken = aliceReg.json().path("token").asText();

        HttpResponseWrapper bobReg = send("POST", "/auth/register", null, Map.of(
                "email", "bob.url." + ts + "@example.com",
                "password", "Password123!",
                "displayName", "Bob URL",
                "timeZone", "Europe/London"
        ));
        assertThat(bobReg.statusCode()).isEqualTo(201);
        String bobToken = bobReg.json().path("token").asText();
        HttpResponseWrapper bobMe = send("GET", "/auth/me", bobToken, null);
        UUID bobId = UUID.fromString(bobMe.json().path("id").asText());

        // 2. Ensure approved skills
        UUID javaId = ensureSkill("Java");
        UUID reactId = ensureSkill("React");

        // Set Alice and Bob skills
        send("PUT", "/me/skills", aliceToken, Map.of("offeredSkillIds", List.of(reactId), "wantedSkillIds", List.of(javaId)));
        send("PUT", "/me/skills", bobToken, Map.of("offeredSkillIds", List.of(javaId), "wantedSkillIds", List.of(reactId)));

        // 3. Create exchange
        HttpResponseWrapper exRes = send("POST", "/exchanges", aliceToken, Map.of(
                "receiverId", bobId.toString(),
                "skillFromReceiver", javaId.toString()
        ));
        assertThat(exRes.statusCode()).isEqualTo(201);
        String exchangeId = exRes.json().path("id").asText();

        // 4. Bob accepts
        send("POST", "/exchanges/" + exchangeId + "/accept", bobToken, Map.of("skillFromRequester", reactId.toString()));

        // 5. Schedule with baseline valid URL
        String baselineUrl = "https://teams.microsoft.com/l/meetup-join/19_test_meeting";
        Instant scheduledAt = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

        HttpResponseWrapper schedRes = send("POST", "/exchanges/" + exchangeId + "/schedule", aliceToken, Map.of(
                "scheduledAt", scheduledAt.toString(),
                "meetingUrl", baselineUrl
        ));
        assertThat(schedRes.statusCode()).isEqualTo(200);
        assertThat(schedRes.json().path("meetingUrl").asText()).isEqualTo(baselineUrl);

        // 6. Test extended meeting URL: 1000 chars (teams.microsoft.com allowlist)
        String prefix = "https://teams.microsoft.com/l/meetup-join/19%3ameeting_";
        String longUrl1000 = prefix + "a".repeat(1000 - prefix.length());
        HttpResponseWrapper sched1000 = send("POST", "/exchanges/" + exchangeId + "/schedule", aliceToken, Map.of(
                "scheduledAt", scheduledAt.plus(1, ChronoUnit.HOURS).toString(),
                "meetingUrl", longUrl1000
        ));
        assertThat(sched1000.statusCode()).isEqualTo(200);
        assertThat(sched1000.json().path("meetingUrl").asText()).isEqualTo(longUrl1000);

        // 7. Test boundary exactly 2048 chars
        String longUrl2048 = prefix + "b".repeat(2048 - prefix.length());
        HttpResponseWrapper sched2048 = send("POST", "/exchanges/" + exchangeId + "/schedule", aliceToken, Map.of(
                "scheduledAt", scheduledAt.plus(2, ChronoUnit.HOURS).toString(),
                "meetingUrl", longUrl2048
        ));
        assertThat(sched2048.statusCode()).isEqualTo(200);
        assertThat(sched2048.json().path("meetingUrl").asText()).isEqualTo(longUrl2048);

        // 8. Test >2048 chars: 2049 chars rejected with 400
        String tooLongUrl = prefix + "c".repeat(2049 - prefix.length());
        HttpResponseWrapper schedTooLong = send("POST", "/exchanges/" + exchangeId + "/schedule", aliceToken, Map.of(
                "scheduledAt", scheduledAt.plus(3, ChronoUnit.HOURS).toString(),
                "meetingUrl", tooLongUrl
        ));
        assertThat(schedTooLong.statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("F1 Atomic Rollback in PostgreSQL: invalid timezone rolls back availability deletions")
    void testF1AtomicRollbackPostgres() throws Exception {
        long ts = System.currentTimeMillis();
        HttpResponseWrapper reg = send("POST", "/auth/register", null, Map.of(
                "email", "f1.pg." + ts + "@example.com",
                "password", "Password123!",
                "displayName", "F1 User",
                "timeZone", "UTC"
        ));
        assertThat(reg.statusCode()).isEqualTo(201);
        String token = reg.json().path("token").asText();

        // Save valid window
        HttpResponseWrapper initialSave = send("PUT", "/me/availability", token, Map.of(
                "timeZone", "UTC",
                "windows", List.of(Map.of("dayOfWeek", "MONDAY", "startTime", "09:00", "endTime", "12:00"))
        ));
        assertThat(initialSave.statusCode()).isEqualTo(200);

        // Attempt replace with invalid timezone
        HttpResponseWrapper invalid = send("PUT", "/me/availability", token, Map.of(
                "timeZone", "Invalid/Zone_Name",
                "windows", List.of(Map.of("dayOfWeek", "FRIDAY", "startTime", "14:00", "endTime", "17:00"))
        ));
        assertThat(invalid.statusCode()).isEqualTo(400);

        // Verify MONDAY window remains intact in PostgreSQL
        HttpResponseWrapper getAvail = send("GET", "/me/availability", token, null);
        assertThat(getAvail.statusCode()).isEqualTo(200);
        assertThat(getAvail.json().size()).isEqualTo(1);
        assertThat(getAvail.json().get(0).path("dayOfWeek").asText()).isEqualTo("MONDAY");
        assertThat(getAvail.json().get(0).path("startTime").asText()).isEqualTo("09:00:00");
    }

    @Test
    @DisplayName("F2 Double-Blind Feedback in PostgreSQL: privacy until dual submit, atomic publication")
    void testF2DoubleBlindFeedbackPostgres() throws Exception {
        long ts = System.currentTimeMillis();

        HttpResponseWrapper aliceReg = send("POST", "/auth/register", null, Map.of(
                "email", "alice.f2." + ts + "@example.com",
                "password", "Password123!",
                "displayName", "Alice F2",
                "timeZone", "America/Sao_Paulo"
        ));
        assertThat(aliceReg.statusCode()).isEqualTo(201);
        String aliceToken = aliceReg.json().path("token").asText();
        HttpResponseWrapper aliceMe = send("GET", "/auth/me", aliceToken, null);
        String aliceId = aliceMe.json().path("id").asText();

        HttpResponseWrapper bobReg = send("POST", "/auth/register", null, Map.of(
                "email", "bob.f2." + ts + "@example.com",
                "password", "Password123!",
                "displayName", "Bob F2",
                "timeZone", "Europe/London"
        ));
        assertThat(bobReg.statusCode()).isEqualTo(201);
        String bobToken = bobReg.json().path("token").asText();
        HttpResponseWrapper bobMe = send("GET", "/auth/me", bobToken, null);
        String bobId = bobMe.json().path("id").asText();

        // Third party user
        HttpResponseWrapper charlieReg = send("POST", "/auth/register", null, Map.of(
                "email", "charlie.f2." + ts + "@example.com",
                "password", "Password123!",
                "displayName", "Charlie F2",
                "timeZone", "UTC"
        ));
        assertThat(charlieReg.statusCode()).isEqualTo(201);
        String charlieToken = charlieReg.json().path("token").asText();

        UUID javaId = ensureSkill("Java");
        UUID reactId = ensureSkill("React");

        send("PUT", "/me/skills", aliceToken, Map.of("offeredSkillIds", List.of(reactId), "wantedSkillIds", List.of(javaId)));
        send("PUT", "/me/skills", bobToken, Map.of("offeredSkillIds", List.of(javaId), "wantedSkillIds", List.of(reactId)));

        // Create, accept, schedule, complete
        HttpResponseWrapper ex = send("POST", "/exchanges", aliceToken, Map.of("receiverId", bobId, "skillFromReceiver", javaId.toString()));
        assertThat(ex.statusCode()).isEqualTo(201);
        String exId = ex.json().path("id").asText();

        send("POST", "/exchanges/" + exId + "/accept", bobToken, Map.of("skillFromRequester", reactId.toString()));
        send("POST", "/exchanges/" + exId + "/schedule", aliceToken, Map.of("scheduledAt", Instant.now().plus(1, ChronoUnit.DAYS).toString(), "meetingUrl", "https://meet.google.com/abc-defg-hij"));
        send("POST", "/exchanges/" + exId + "/complete", bobToken, null);

        // Alice submits feedback (rating 5)
        HttpResponseWrapper fbAlice = send("POST", "/exchanges/" + exId + "/feedback", aliceToken, Map.of("rating", 5, "comment", "Great Java lesson"));
        assertThat(fbAlice.statusCode()).isEqualTo(201);

        // Bob checks: blinded!
        HttpResponseWrapper bobCheck = send("GET", "/exchanges/" + exId + "/feedback", bobToken, null);
        assertThat(bobCheck.json().path("mine").isNull()).isTrue();
        assertThat(bobCheck.json().path("theirs").isNull()).isTrue();
        assertThat(bobCheck.json().path("counterpartSubmitted").asBoolean()).isTrue();

        // Bob's profile feedback queried by Bob or third party: 0 reviews
        HttpResponseWrapper bobFbByBob = send("GET", "/users/" + bobId + "/feedback", bobToken, null);
        assertThat(bobFbByBob.json().size()).isEqualTo(0);

        HttpResponseWrapper bobFbByCharlie = send("GET", "/users/" + bobId + "/feedback", charlieToken, null);
        assertThat(bobFbByCharlie.json().size()).isEqualTo(0);

        // Bob reputation in profile: 0 count, 0 average
        HttpResponseWrapper bobProfileBefore = send("GET", "/users/" + bobId, charlieToken, null);
        assertThat(bobProfileBefore.json().path("reputationCount").asInt()).isEqualTo(0);

        // Bob submits feedback (rating 4)
        HttpResponseWrapper fbBob = send("POST", "/exchanges/" + exId + "/feedback", bobToken, Map.of("rating", 4, "comment", "Quick learner"));
        assertThat(fbBob.statusCode()).isEqualTo(201);

        // Now both published!
        // User B received User A's review (rating 5)
        HttpResponseWrapper bobPublished = send("GET", "/users/" + bobId + "/feedback", charlieToken, null);
        assertThat(bobPublished.json().size()).isEqualTo(1);
        assertThat(bobPublished.json().get(0).path("rating").asInt()).isEqualTo(5);

        // User A received User B's review (rating 4)
        HttpResponseWrapper alicePublished = send("GET", "/users/" + aliceId + "/feedback", charlieToken, null);
        assertThat(alicePublished.json().size()).isEqualTo(1);
        assertThat(alicePublished.json().get(0).path("rating").asInt()).isEqualTo(4);

        // Bob and Alice reputations updated in PostgreSQL
        HttpResponseWrapper bobProfileAfter = send("GET", "/users/" + bobId, charlieToken, null);
        assertThat(bobProfileAfter.json().path("reputationCount").asInt()).isEqualTo(1);
        assertThat(bobProfileAfter.json().path("reputationAverage").asDouble()).isEqualTo(5.0);

        HttpResponseWrapper aliceProfileAfter = send("GET", "/users/" + aliceId, charlieToken, null);
        assertThat(aliceProfileAfter.json().path("reputationCount").asInt()).isEqualTo(1);
        assertThat(aliceProfileAfter.json().path("reputationAverage").asDouble()).isEqualTo(4.0);
    }

    @Test
    @DisplayName("Concurrency: Simultaneous feedback submissions by same author yield 201/409 with exactly one row in PostgreSQL")
    void testConcurrentFeedbackSubmissionPostgres() throws Exception {
        long ts = System.currentTimeMillis();

        HttpResponseWrapper aliceReg = send("POST", "/auth/register", null, Map.of(
                "email", "alice.cfb." + ts + "@example.com",
                "password", "Password123!",
                "displayName", "Alice CFB",
                "timeZone", "UTC"
        ));
        String aliceToken = aliceReg.json().path("token").asText();
        String aliceId = send("GET", "/auth/me", aliceToken, null).json().path("id").asText();

        HttpResponseWrapper bobReg = send("POST", "/auth/register", null, Map.of(
                "email", "bob.cfb." + ts + "@example.com",
                "password", "Password123!",
                "displayName", "Bob CFB",
                "timeZone", "UTC"
        ));
        String bobToken = bobReg.json().path("token").asText();
        String bobId = send("GET", "/auth/me", bobToken, null).json().path("id").asText();

        UUID javaId = ensureSkill("Java");
        UUID reactId = ensureSkill("React");

        send("PUT", "/me/skills", aliceToken, Map.of("offeredSkillIds", List.of(reactId), "wantedSkillIds", List.of(javaId)));
        send("PUT", "/me/skills", bobToken, Map.of("offeredSkillIds", List.of(javaId), "wantedSkillIds", List.of(reactId)));

        HttpResponseWrapper ex = send("POST", "/exchanges", aliceToken, Map.of("receiverId", bobId, "skillFromReceiver", javaId.toString()));
        String exId = ex.json().path("id").asText();
        send("POST", "/exchanges/" + exId + "/accept", bobToken, Map.of("skillFromRequester", reactId.toString()));
        send("POST", "/exchanges/" + exId + "/schedule", aliceToken, Map.of("scheduledAt", Instant.now().plus(1, ChronoUnit.DAYS).toString(), "meetingUrl", "https://meet.google.com/test-room-cfb"));
        send("POST", "/exchanges/" + exId + "/complete", bobToken, null);

        // Fire 2 simultaneous feedback submissions from Alice
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<HttpResponseWrapper>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int idx = i;
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return send("POST", "/exchanges/" + exId + "/feedback", aliceToken, Map.of(
                        "rating", 5,
                        "comment", "Concurrent review attempt " + idx
                ));
            }));
        }

        ready.await();
        start.countDown();

        List<HttpResponseWrapper> responses = new ArrayList<>();
        for (var f : futures) {
            responses.add(f.get(10, TimeUnit.SECONDS));
        }
        executor.shutdown();

        List<Integer> statusCodes = responses.stream().map(HttpResponseWrapper::statusCode).sorted().toList();
        assertThat(statusCodes)
                .as("One submission must succeed with 201 and duplicate must be rejected with 409 under row lock")
                .containsExactly(201, 409);

        // Verify PostgreSQL table state: exactly 1 row for Alice's feedback
        List<Feedback> feedbackRows = feedbackRepository.findByExchangeId(UUID.fromString(exId));
        assertThat(feedbackRows).hasSize(1);
        assertThat(feedbackRows.get(0).getAuthor().getId()).isEqualTo(UUID.fromString(aliceId));
    }

    @Test
    @DisplayName("Concurrency: Simultaneous F1 availability replacements preserve integrity under PostgreSQL row lock")
    void testConcurrentAvailabilityReplacementPostgres() throws Exception {
        long ts = System.currentTimeMillis();
        HttpResponseWrapper reg = send("POST", "/auth/register", null, Map.of(
                "email", "f1.conc." + ts + "@example.com",
                "password", "Password123!",
                "displayName", "F1 Conc User",
                "timeZone", "UTC"
        ));
        String token = reg.json().path("token").asText();
        String userId = send("GET", "/auth/me", token, null).json().path("id").asText();

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<HttpResponseWrapper>> futures = new ArrayList<>();
        // Thread A: MONDAY 09:00 - 12:00
        futures.add(executor.submit(() -> {
            ready.countDown();
            start.await();
            return send("PUT", "/me/availability", token, Map.of(
                    "timeZone", "UTC",
                    "windows", List.of(Map.of("dayOfWeek", "MONDAY", "startTime", "09:00", "endTime", "12:00"))
            ));
        }));
        // Thread B: FRIDAY 14:00 - 17:00
        futures.add(executor.submit(() -> {
            ready.countDown();
            start.await();
            return send("PUT", "/me/availability", token, Map.of(
                    "timeZone", "UTC",
                    "windows", List.of(Map.of("dayOfWeek", "FRIDAY", "startTime", "14:00", "endTime", "17:00"))
            ));
        }));

        ready.await();
        start.countDown();

        for (var f : futures) {
            assertThat(f.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
        }
        executor.shutdown();

        // Under PESSIMISTIC_WRITE lock on user row, deletions and insertions do not interleave
        // Final state must contain exactly 1 window in PostgreSQL
        List<Availability> windows = availabilityRepository.findByUserId(UUID.fromString(userId));
        assertThat(windows).hasSize(1);
        String day = windows.get(0).getDayOfWeek().name();
        assertThat(day).isIn("MONDAY", "FRIDAY");
    }

    @Test
    @DisplayName("Concurrency: Simultaneous complete and cancel dispute yields 200/409 with consistent terminal status in PostgreSQL")
    void testConcurrentTerminalExchangeStateTransition() throws Exception {
        long ts = System.currentTimeMillis();

        HttpResponseWrapper aliceReg = send("POST", "/auth/register", null, Map.of(
                "email", "alice.term." + ts + "@example.com",
                "password", "Password123!",
                "displayName", "Alice Term",
                "timeZone", "UTC"
        ));
        String aliceToken = aliceReg.json().path("token").asText();

        HttpResponseWrapper bobReg = send("POST", "/auth/register", null, Map.of(
                "email", "bob.term." + ts + "@example.com",
                "password", "Password123!",
                "displayName", "Bob Term",
                "timeZone", "UTC"
        ));
        String bobToken = bobReg.json().path("token").asText();
        String bobId = send("GET", "/auth/me", bobToken, null).json().path("id").asText();

        UUID javaId = ensureSkill("Java");
        UUID reactId = ensureSkill("React");

        send("PUT", "/me/skills", aliceToken, Map.of("offeredSkillIds", List.of(reactId), "wantedSkillIds", List.of(javaId)));
        send("PUT", "/me/skills", bobToken, Map.of("offeredSkillIds", List.of(javaId), "wantedSkillIds", List.of(reactId)));

        HttpResponseWrapper ex = send("POST", "/exchanges", aliceToken, Map.of("receiverId", bobId, "skillFromReceiver", javaId.toString()));
        String exId = ex.json().path("id").asText();
        send("POST", "/exchanges/" + exId + "/accept", bobToken, Map.of("skillFromRequester", reactId.toString()));
        send("POST", "/exchanges/" + exId + "/schedule", aliceToken, Map.of("scheduledAt", Instant.now().plus(1, ChronoUnit.DAYS).toString(), "meetingUrl", "https://meet.google.com/test-room-term"));

        // Simultaneously dispute complete vs cancel
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<HttpResponseWrapper>> futures = new ArrayList<>();
        // Alice completes
        futures.add(executor.submit(() -> {
            ready.countDown();
            start.await();
            return send("POST", "/exchanges/" + exId + "/complete", aliceToken, null);
        }));
        // Bob cancels
        futures.add(executor.submit(() -> {
            ready.countDown();
            start.await();
            return send("POST", "/exchanges/" + exId + "/cancel", bobToken, null);
        }));

        ready.await();
        start.countDown();

        List<HttpResponseWrapper> responses = new ArrayList<>();
        for (var f : futures) {
            responses.add(f.get(10, TimeUnit.SECONDS));
        }
        executor.shutdown();

        List<Integer> statuses = responses.stream().map(HttpResponseWrapper::statusCode).sorted().toList();
        assertThat(statuses)
                .as("One terminal transition must succeed (200) and the disputed transition must fail with 409")
                .containsExactly(200, 409);

        // Terminal status in PostgreSQL must be consistent
        HttpResponseWrapper finalEx = send("GET", "/exchanges/" + exId, aliceToken, null);
        assertThat(finalEx.statusCode()).isEqualTo(200);
        String finalStatus = finalEx.json().path("status").asText();
        assertThat(finalStatus).isIn("COMPLETED", "CANCELLED");
    }

    @Test
    @DisplayName("Skill Vocabulary Identity: C, C++, C# maintain distinct non-colliding identities in PostgreSQL; punctuation-only rejected with 400")
    void testSkillSlugsAndIdentityDisambiguationPostgres() throws Exception {
        long ts = System.currentTimeMillis();
        HttpResponseWrapper reg = send("POST", "/auth/register", null, Map.of(
                "email", "slug.test." + ts + "@example.com",
                "password", "Password123!",
                "displayName", "Slug User",
                "timeZone", "UTC"
        ));
        String token = reg.json().path("token").asText();

        // 1. Suggest C, C++, C#
        HttpResponseWrapper resC = send("POST", "/skills/suggest", token, Map.of("name", "C"));
        assertThat(resC.statusCode()).isEqualTo(201);
        String idC = resC.json().path("id").asText();
        String slugC = resC.json().path("slug").asText();

        HttpResponseWrapper resCpp = send("POST", "/skills/suggest", token, Map.of("name", "C++"));
        assertThat(resCpp.statusCode()).isEqualTo(201);
        String idCpp = resCpp.json().path("id").asText();
        String slugCpp = resCpp.json().path("slug").asText();

        HttpResponseWrapper resCSharp = send("POST", "/skills/suggest", token, Map.of("name", "C#"));
        assertThat(resCSharp.statusCode()).isEqualTo(201);
        String idCSharp = resCSharp.json().path("id").asText();
        String slugCSharp = resCSharp.json().path("slug").asText();

        // Assert distinct identities and non-colliding slugs
        assertThat(Set.of(idC, idCpp, idCSharp)).hasSize(3);
        assertThat(Set.of(slugC, slugCpp, slugCSharp)).hasSize(3);
        assertThat(slugC).isEqualTo("c");
        assertThat(slugCpp).contains("~2b");
        assertThat(slugCSharp).contains("~23");

        // 2. Idempotent re-suggestion of C++ returns existing skill ID without duplicating
        HttpResponseWrapper resCppAgain = send("POST", "/skills/suggest", token, Map.of("name", "C++"));
        assertThat(resCppAgain.statusCode()).isEqualTo(201);
        assertThat(resCppAgain.json().path("id").asText()).isEqualTo(idCpp);

        // 3. Punctuation-only / meaningless suggestions rejected with 400 INVALID_SKILL_NAME
        HttpResponseWrapper resPunct1 = send("POST", "/skills/suggest", token, Map.of("name", "###"));
        assertThat(resPunct1.statusCode()).isEqualTo(400);
        assertThat(resPunct1.json().path("code").asText()).isEqualTo("INVALID_SKILL_NAME");

        HttpResponseWrapper resPunct2 = send("POST", "/skills/suggest", token, Map.of("name", "+++"));
        assertThat(resPunct2.statusCode()).isEqualTo(400);
        assertThat(resPunct2.json().path("code").asText()).isEqualTo("INVALID_SKILL_NAME");
    }

    @Test
    @DisplayName("Redis fail-closed: when Redis is unavailable on controlled offline port, throws 503 RATE_LIMIT_UNAVAILABLE")
    void testRedisFailClosedControlled() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory("127.0.0.1", 16380);
        factory.afterPropertiesSet();
        StringRedisTemplate badRedis = new StringRedisTemplate(factory);
        RateLimiterService offlineLimiter = new RateLimiterService(badRedis);

        assertThatThrownBy(() -> offlineLimiter.tryConsume("test:offline:key", 5, 60))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(apiEx.getCode()).isEqualTo("RATE_LIMIT_UNAVAILABLE");
                });
        factory.destroy();
    }
}
