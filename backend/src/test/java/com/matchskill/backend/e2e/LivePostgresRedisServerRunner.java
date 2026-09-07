package com.matchskill.backend.e2e;

import com.matchskill.backend.BackendApplication;
import com.matchskill.backend.entity.Skill;
import com.matchskill.backend.entity.SkillStatus;
import com.matchskill.backend.repository.SkillRepository;
import com.matchskill.backend.util.Slugs;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Dedicated test-only server runner for live validation with REAL PostgreSQL and REAL Redis.
 * Uses real Redis Lua rate limiter (no mocked RateLimiterService).
 * Does NOT declare @SpringBootApplication to avoid test classpath bean pollution.
 */
public class LivePostgresRedisServerRunner {

    @TestConfiguration
    public static class LocalPostgresRedisServerConfig {

        @Bean("seedPostgresApprovedSkills")
        public CommandLineRunner seedPostgresApprovedSkills(SkillRepository skillRepository) {
            return args -> {
                List<String> approved = List.of("Java", "React", "Python", "TypeScript", "Spring Boot", "SQL");
                for (String s : approved) {
                    String slug = Slugs.slugify(s);
                    skillRepository.findBySlug(slug).orElseGet(() ->
                            skillRepository.save(Skill.builder()
                                    .name(s)
                                    .slug(slug)
                                    .status(SkillStatus.APPROVED)
                                    .build()));
                }
            };
        }
    }

    public static void main(String[] args) {
        String dbHost = System.getProperty("test.db.host", System.getenv().getOrDefault("TEST_DB_HOST", "127.0.0.1"));
        String dbPort = System.getProperty("test.db.port", System.getenv().getOrDefault("TEST_DB_PORT", "15432"));
        String dbName = System.getProperty("test.db.name", System.getenv().getOrDefault("TEST_DB_NAME", "matchskill_validation"));
        String dbUser = System.getProperty("test.db.user", System.getenv().getOrDefault("TEST_DB_USER", "matchskill_validation"));
        String dbPass = System.getProperty("test.db.pass", System.getenv().getOrDefault("TEST_DB_PASS", "local-validation-only"));

        String redisHost = System.getProperty("test.redis.host", System.getenv().getOrDefault("TEST_REDIS_HOST", "127.0.0.1"));
        String redisPort = System.getProperty("test.redis.port", System.getenv().getOrDefault("TEST_REDIS_PORT", "16379"));

        String serverPort = System.getProperty("server.port", System.getenv().getOrDefault("TEST_SERVER_PORT", "18089"));

        System.setProperty("spring.datasource.url", "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName);
        System.setProperty("spring.datasource.username", dbUser);
        System.setProperty("spring.datasource.password", dbPass);
        System.setProperty("spring.datasource.driver-class-name", "org.postgresql.Driver");
        System.setProperty("spring.jpa.hibernate.ddl-auto", "update");

        System.setProperty("spring.data.redis.host", redisHost);
        System.setProperty("spring.data.redis.port", redisPort);

        System.setProperty("spring.security.oauth2.client.registration.google.client-id", "");
        System.setProperty("spring.security.oauth2.client.registration.google.client-secret", "");
        System.setProperty("server.port", serverPort);
        System.setProperty("server.servlet.context-path", "/api");
        System.setProperty("app.cors.allowed-origins", "http://127.0.0.1:4176,http://localhost:4176,http://127.0.0.1:4174,http://localhost:4174,http://127.0.0.1:5173,http://localhost:5173,http://localhost:3000");

        SpringApplication app = new SpringApplication(BackendApplication.class, LocalPostgresRedisServerConfig.class);
        app.run(args);
    }
}
