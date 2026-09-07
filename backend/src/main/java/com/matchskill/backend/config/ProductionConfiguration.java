package com.matchskill.backend.config;

import java.util.List;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/** Rejects incomplete production settings before creating clients or connecting to storage. */
@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class ProductionConfiguration {

    @Bean
    static BeanFactoryPostProcessor requireProductionSettings(Environment environment) {
        return beanFactory -> {
            for (String property : List.of(
                    "app.jwt.secret",
                    "spring.datasource.url",
                    "spring.datasource.username",
                    "spring.datasource.password",
                    "spring.datasource.hikari.data-source-properties.sslmode",
                    "spring.data.redis.url",
                    "app.cors.allowed-origins",
                    "app.oauth2.success-redirect-uri")) {
                requireText(environment, property);
            }
            String google = "spring.security.oauth2.client.registration.google.";
            if (StringUtils.hasText(environment.getProperty(google + "client-id"))
                    && StringUtils.hasText(environment.getProperty(google + "client-secret"))) {
                requireText(environment, google + "redirect-uri");
            }
        };
    }

    private static void requireText(Environment environment, String property) {
        String value;
        try {
            value = environment.getProperty(property);
        } catch (IllegalArgumentException exception) {
            throw missingProperty(property);
        }
        if (!StringUtils.hasText(value)) {
            throw missingProperty(property);
        }
    }

    private static IllegalStateException missingProperty(String property) {
        return new IllegalStateException("Production setting '" + property + "' must be configured");
    }
}
