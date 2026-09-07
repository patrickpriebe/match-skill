package com.matchskill.backend.service;

import com.matchskill.backend.dto.auth.AuthResponse;
import com.matchskill.backend.dto.auth.LoginRequest;
import com.matchskill.backend.dto.auth.MeResponse;
import com.matchskill.backend.dto.auth.RegisterRequest;
import com.matchskill.backend.entity.User;
import com.matchskill.backend.exception.ApiException;
import com.matchskill.backend.repository.UserRepository;
import com.matchskill.backend.security.JwtService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "Email is already registered");
        }
        User user =
                User.builder()
                        .email(request.email())
                        .passwordHash(passwordEncoder.encode(request.password()))
                        .displayName(request.displayName())
                        .timeZone(request.timeZone())
                        .skillsRegistered(false)
                        .build();
        userRepository.save(user);
        return new AuthResponse(jwtService.generateToken(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user =
                userRepository
                        .findByEmail(request.email())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.UNAUTHORIZED,
                                                "INVALID_CREDENTIALS",
                                                "Email or password is incorrect"));
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect");
        }
        return new AuthResponse(jwtService.generateToken(user));
    }

    /**
     * Links a Google identity to an existing account matched by email, or creates a new one.
     * Called from the OAuth2 success handler once Google confirms the identity.
     */
    @Transactional
    public User findOrCreateGoogleUser(String googleSubject, String email, String displayName) {
        User user =
                userRepository
                        .findByGoogleSubject(googleSubject)
                        .or(
                                () ->
                                        userRepository
                                                .findByEmail(email)
                                                .map(
                                                        existing -> {
                                                            existing.setGoogleSubject(googleSubject);
                                                            return existing;
                                                        }))
                        .orElseGet(
                                () ->
                                        User.builder()
                                                .email(email)
                                                .googleSubject(googleSubject)
                                                .displayName(displayName != null ? displayName : email)
                                                .timeZone("UTC")
                                                .skillsRegistered(false)
                                                .build());
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public MeResponse currentUser(UUID userId) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
        return toMeResponse(user);
    }

    public MeResponse toMeResponse(User user) {
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getBio(),
                user.getTimeZone(),
                user.isSkillsRegistered());
    }
}
