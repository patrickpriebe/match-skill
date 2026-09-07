package com.matchskill.backend.security;

import com.matchskill.backend.config.OAuth2Properties;
import com.matchskill.backend.entity.User;
import com.matchskill.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Links or creates the local account for the Google identity, then redirects
 * to the frontend with our own JWT — the frontend is a separate SPA, so the
 * OAuth2 code exchange cannot end in a JSON response.
 */
@Component
public class GoogleOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final JwtService jwtService;
    private final OAuth2Properties oAuth2Properties;

    public GoogleOAuth2SuccessHandler(
            AuthService authService, JwtService jwtService, OAuth2Properties oAuth2Properties) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.oAuth2Properties = oAuth2Properties;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String googleSubject = oAuth2User.getAttribute("sub");
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        User user = authService.findOrCreateGoogleUser(googleSubject, email, name);
        String token = jwtService.generateToken(user);

        String redirectUrl =
                oAuth2Properties.successRedirectUri()
                        + "?token="
                        + URLEncoder.encode(token, StandardCharsets.UTF_8);
        response.sendRedirect(redirectUrl);
    }
}
