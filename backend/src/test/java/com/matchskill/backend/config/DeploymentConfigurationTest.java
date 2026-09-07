package com.matchskill.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

class DeploymentConfigurationTest {

    private static final String VALID_SECRET = "test-only-production-secret-at-least-32-characters";

    @Test
    void shouldKeepLocalDefaultsAndUseTheActualFrontendCallback() {
        configuration(Map.of()).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getActiveProfiles()).isEmpty();
            assertThat(context.getBean(ServerProperties.class).getPort()).isEqualTo(8080);
            assertThat(context.getBean(ServerProperties.class).getServlet().getContextPath()).isEqualTo("/api");
            assertThat(context.getBean(HibernateProperties.class).getDdlAuto()).isEqualTo("update");
            assertThat(context.getBean(DataSourceProperties.class).getUrl())
                    .isEqualTo("jdbc:postgresql://localhost:5432/matchskill");
            assertThat(context.getBean(JwtProperties.class).secret())
                    .isEqualTo("dev-only-secret-change-me-dev-only-secret-change-me");
            assertThat(context.getBean(OAuth2Properties.class).successRedirectUri())
                    .isEqualTo("http://localhost:5173/auth/callback");
        });
    }

    @Test
    void shouldHonorPortAndFrontendCallbackEnvironmentOverrides() {
        configuration(Map.of("PORT", "10000", "OAUTH2_SUCCESS_REDIRECT_URI",
                "https://frontend.example.com/auth/callback")).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ServerProperties.class).getPort()).isEqualTo(10000);
            assertThat(context.getBean(OAuth2Properties.class).successRedirectUri())
                    .isEqualTo("https://frontend.example.com/auth/callback");
        });
    }

    @Test
    void shouldValidateProductionSchemaAndRequireVerifiedPostgresTlsByDefault() {
        production(productionEnvironment()).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(HibernateProperties.class).getDdlAuto()).isEqualTo("validate");
            assertThat(context.getBean(HikariConfig.class).getDataSourceProperties())
                    .containsEntry("sslmode", "verify-full");
            assertThat(context.getBean(DataSourceProperties.class).getUrl())
                    .isEqualTo("jdbc:postgresql://database.example.com:5432/matchskill?sslrootcert=/etc/secrets/root.crt");
            assertThat(context.getBean(DataSourceProperties.class).getUsername()).isEqualTo("app_user");
            assertThat(context.getBean(JwtProperties.class).secret()).isEqualTo(VALID_SECRET);
            assertThat(context.getBean(ServerProperties.class).getAddress().getHostAddress())
                    .isEqualTo("0.0.0.0");
            assertThat(context.getBean(CorsProperties.class).allowedOrigins())
                    .containsExactly("https://frontend.example.com");
            assertThat(context.getEnvironment().getProperty("spring.data.redis.database", Integer.class))
                    .isZero();
        });
    }

    @ParameterizedTest
    @CsvSource({
            "JWT_SECRET,app.jwt.secret",
            "JDBC_DATABASE_URL,spring.datasource.url",
            "DB_USER,spring.datasource.username",
            "DB_PASSWORD,spring.datasource.password",
            "REDIS_URL,spring.data.redis.url",
            "CORS_ALLOWED_ORIGINS,app.cors.allowed-origins",
            "OAUTH2_SUCCESS_REDIRECT_URI,app.oauth2.success-redirect-uri"
    })
    void shouldRejectMissingProductionSettingsBeforeClientInitialization(String variable, String property) {
        var environment = productionEnvironment();
        environment.remove(variable);

        production(environment).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasMessageContaining("Production setting '" + property + "' must be configured");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"JWT_SECRET", "JDBC_DATABASE_URL", "DB_USER", "DB_PASSWORD",
            "REDIS_URL", "CORS_ALLOWED_ORIGINS", "OAUTH2_SUCCESS_REDIRECT_URI", "DB_SSLMODE"})
    void shouldRejectBlankProductionSettings(String variable) {
        var environment = productionEnvironment();
        environment.put(variable, " ");

        production(environment).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRetainJwtMinimumLengthValidationInProduction() {
        var environment = productionEnvironment();
        environment.put("JWT_SECRET", "short-test-secret");

        production(environment).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("app.jwt")
                    .hasStackTraceContaining("Size.app.jwt.secret");
        });
    }

    @Test
    void shouldAllowExplicitTlsOverrideForIsolatedDisposableValidation() {
        var environment = productionEnvironment();
        environment.put("DB_SSLMODE", "disable");

        production(environment).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(HikariConfig.class).getDataSourceProperties())
                    .containsEntry("sslmode", "disable");
        });
    }

    @ParameterizedTest
    @CsvSource({"rediss,true", "redis,false"})
    void shouldUseRedisUrlCredentialsDatabaseAndTlsWithoutOpeningAConnection(String scheme, boolean tls) {
        var environment = productionEnvironment();
        environment.put("REDIS_DATABASE", "2");
        environment.put("REDIS_URL", scheme
                + "://default:test-only-redis-password@redis.example.com:6380/9");

        production(environment).withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var factory = context.getBean(LettuceConnectionFactory.class);
                    assertThat(factory.getHostName()).isEqualTo("redis.example.com");
                    assertThat(factory.getPort()).isEqualTo(6380);
                    assertThat(factory.getStandaloneConfiguration().getDatabase()).isEqualTo(2);
                    assertThat(factory.getStandaloneConfiguration().getUsername()).isEqualTo("default");
                    assertThat(factory.getStandaloneConfiguration().getPassword().get())
                            .isEqualTo("test-only-redis-password".toCharArray());
                    assertThat(factory.getClientConfiguration().isUseSsl()).isEqualTo(tls);
                    assertThat(factory.getClientConfiguration().getCommandTimeout()).isEqualTo(Duration.ofSeconds(2));
                });
    }

    @Test
    void shouldPreserveNativeSpringPropertyOverrides() {
        var environment = productionEnvironment();
        environment.remove("JDBC_DATABASE_URL");
        environment.remove("REDIS_URL");
        environment.remove("JWT_SECRET");

        production(environment).withPropertyValues(
                "spring.datasource.url=jdbc:postgresql://override.example.com/matchskill",
                "spring.data.redis.url=rediss://override.example.com:6380",
                "app.jwt.secret=" + VALID_SECRET).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(DataSourceProperties.class).getUrl())
                    .isEqualTo("jdbc:postgresql://override.example.com/matchskill");
        });
    }

    @Test
    void shouldRequireExplicitBackendCallbackOnlyWhenGoogleIsConfigured() {
        var environment = productionEnvironment();
        environment.put("GOOGLE_CLIENT_ID", "test-client");
        environment.put("GOOGLE_CLIENT_SECRET", "test-client-secret");

        production(environment).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessageContaining(
                    "Production setting 'spring.security.oauth2.client.registration.google.redirect-uri'");
        });

        environment.put("GOOGLE_REDIRECT_URI", "https://backend.example.com/api/auth/google/callback");
        production(environment).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty(
                    "spring.security.oauth2.client.registration.google.redirect-uri"))
                    .isEqualTo("https://backend.example.com/api/auth/google/callback");
        });
    }

    private ApplicationContextRunner production(Map<String, String> values) {
        return configuration(values).withPropertyValues("spring.profiles.active=prod");
    }

    private ApplicationContextRunner configuration(Map<String, String> values) {
        return new ApplicationContextRunner().withInitializer(context -> {
            var sources = context.getEnvironment().getPropertySources();
            // Test fixtures must never consume credentials or profile settings from this workstation.
            sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            sources.addLast(new MapPropertySource("deployment-test-environment", new LinkedHashMap<>(values)));
            new ConfigDataApplicationContextInitializer().initialize(context);
        }).withUserConfiguration(PropertiesConfiguration.class, ProductionConfiguration.class);
    }

    private Map<String, String> productionEnvironment() {
        var values = new LinkedHashMap<String, String>();
        values.put("JDBC_DATABASE_URL",
                "jdbc:postgresql://database.example.com:5432/matchskill?sslrootcert=/etc/secrets/root.crt");
        values.put("DB_USER", "app_user");
        values.put("DB_PASSWORD", "test-only-database-password");
        values.put("REDIS_URL", "rediss://default:test-only-redis-password@redis.example.com:6380");
        values.put("JWT_SECRET", VALID_SECRET);
        values.put("CORS_ALLOWED_ORIGINS", "https://frontend.example.com");
        values.put("OAUTH2_SUCCESS_REDIRECT_URI", "https://frontend.example.com/auth/callback");
        return values;
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties({JwtProperties.class, OAuth2Properties.class, CorsProperties.class,
            ServerProperties.class, DataSourceProperties.class, HibernateProperties.class})
    static class PropertiesConfiguration {
        @Bean
        @ConfigurationProperties("spring.datasource.hikari")
        HikariConfig hikariConfig() {
            return new HikariConfig();
        }
    }
}
