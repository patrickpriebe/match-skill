package com.matchskill.backend.e2e;

import com.matchskill.backend.BackendApplication;
import com.matchskill.backend.entity.Skill;
import com.matchskill.backend.entity.SkillStatus;
import com.matchskill.backend.repository.SkillRepository;
import com.matchskill.backend.service.RateLimiterService;
import com.matchskill.backend.util.Slugs;
import org.mockito.Mockito;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * Dedicated test-only server runner for live HTTP socket validation.
 * Uses discardable in-memory H2, test-only mocked rate limiter storage,
 * and seeds initial approved skills into the database.
 * Does NOT declare @SpringBootApplication to avoid test classpath bean pollution.
 */
public class LiveServerRunner {

    @TestConfiguration
    public static class LocalH2ServerConfig {

        @Bean
        @Primary
        public RateLimiterService inMemoryTestRateLimiter() {
            RateLimiterService limiter = Mockito.mock(RateLimiterService.class);
            Mockito.when(limiter.tryConsume(Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt())).thenReturn(true);
            return limiter;
        }

        @Bean
        public CommandLineRunner seedApprovedSkills(SkillRepository skillRepository) {
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
        System.setProperty("spring.datasource.url", "jdbc:h2:mem:liveserver;DB_CLOSE_DELAY=-1");
        System.setProperty("spring.datasource.driver-class-name", "org.h2.Driver");
        System.setProperty("spring.jpa.hibernate.ddl-auto", "create-drop");
        System.setProperty("spring.security.oauth2.client.registration.google.client-id", "");
        System.setProperty("spring.security.oauth2.client.registration.google.client-secret", "");
        System.setProperty("server.port", "8089");
        System.setProperty("server.servlet.context-path", "/api");
        System.setProperty("app.cors.allowed-origins", "http://127.0.0.1:4174,http://localhost:4174,http://127.0.0.1:5173,http://localhost:5173,http://localhost:3000");

        SpringApplication app = new SpringApplication(BackendApplication.class, LocalH2ServerConfig.class);
        app.run(args);
    }
}
