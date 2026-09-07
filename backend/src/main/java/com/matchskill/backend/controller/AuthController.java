package com.matchskill.backend.controller;

import com.matchskill.backend.dto.auth.AuthResponse;
import com.matchskill.backend.dto.auth.LoginRequest;
import com.matchskill.backend.dto.auth.MeResponse;
import com.matchskill.backend.dto.auth.RegisterRequest;
import com.matchskill.backend.security.CurrentUser;
import com.matchskill.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /auth/google and GET /auth/google/callback are not mapped here — the
 * Spring Security OAuth2 login filter chain (see SecurityConfig) intercepts
 * both before dispatch reaches a controller.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        return authService.currentUser(CurrentUser.id(authentication));
    }
}
