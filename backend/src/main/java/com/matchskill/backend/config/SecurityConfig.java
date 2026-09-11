package com.matchskill.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matchskill.backend.dto.error.ApiError;
import com.matchskill.backend.security.GoogleOAuth2SuccessHandler;
import com.matchskill.backend.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientPropertiesMapper;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Conditional(GoogleConfiguredCondition.class)
    public ClientRegistrationRepository clientRegistrationRepository(Environment environment) {
        OAuth2ClientProperties properties = Binder.get(environment)
                .bind("spring.security.oauth2.client", OAuth2ClientProperties.class)
                .orElseThrow(() -> new IllegalStateException("Google OAuth configuration is missing"));
        properties.validate();
        return new InMemoryClientRegistrationRepository(
                List.copyOf(new OAuth2ClientPropertiesMapper(properties).asClientRegistrations().values()));
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            GoogleOAuth2SuccessHandler googleOAuth2SuccessHandler,
            CorsProperties corsProperties,
            OAuth2Properties oAuth2Properties,
            ObjectMapper objectMapper,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource(corsProperties)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getOutputStream(),
                                    new ApiError("UNAUTHORIZED", "Authentication is required"));
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getOutputStream(),
                                    new ApiError("FORBIDDEN", "Access is denied"));
                        }))
                .authorizeHttpRequests(
                        authorize ->
                                authorize
                                        .requestMatchers(
                                                "/auth/register",
                                                "/auth/login",
                                                "/auth/google",
                                                "/auth/google/callback",
                                                "/auth/google/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        ClientRegistrationRepository registrations = clientRegistrations.getIfAvailable();
        if (registrations != null) {
            http.oauth2Login(
                        oauth2 ->
                                oauth2
                                        .authorizationEndpoint(a -> a
                                                .authorizationRequestResolver(googleAuthorizationResolver(registrations)))
                                        .redirectionEndpoint(r -> r.baseUri("/auth/google/callback"))
                                        .successHandler(googleOAuth2SuccessHandler)
                                        // The frontend is a separate SPA: a failure here must land the
                                        // browser back on it, not on this API's own origin with a raw
                                        // JSON body. /auth/callback with no token already renders the
                                        // "sign-in failed, try again" state (see AuthCallbackPage).
                                        .failureHandler((request, response, exception) -> {
                                            log.warn("Google OAuth2 sign-in failed", exception);
                                            response.sendRedirect(oAuth2Properties.successRedirectUri());
                                        }));
        }
        return http.build();
    }

    private OAuth2AuthorizationRequestResolver googleAuthorizationResolver(
            ClientRegistrationRepository registrations) {
        var delegate = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/auth");
        var googleRoute = new AntPathRequestMatcher("/auth/google", "GET");
        // The default /auth/{registrationId} matcher also captures local login and profile routes.
        return new OAuth2AuthorizationRequestResolver() {
            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                return googleRoute.matches(request) ? delegate.resolve(request) : null;
            }

            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId) {
                return googleRoute.matches(request) && "google".equals(registrationId)
                        ? delegate.resolve(request, registrationId) : null;
            }
        };
    }

    private CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    static class GoogleConfiguredCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String prefix = "spring.security.oauth2.client.registration.google.";
            return StringUtils.hasText(context.getEnvironment().getProperty(prefix + "client-id"))
                    && StringUtils.hasText(context.getEnvironment().getProperty(prefix + "client-secret"));
        }
    }
}
