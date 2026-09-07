package com.matchskill.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.matchskill.backend.dto.auth.AuthResponse;
import com.matchskill.backend.dto.auth.LoginRequest;
import com.matchskill.backend.dto.auth.MeResponse;
import com.matchskill.backend.dto.auth.RegisterRequest;
import com.matchskill.backend.entity.User;
import com.matchskill.backend.exception.ApiException;
import com.matchskill.backend.repository.UserRepository;
import com.matchskill.backend.security.JwtService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("Register success: encodes password, persists user, returns JWT")
    void shouldRegisterNewUserSuccessfully() {
        RegisterRequest request = new RegisterRequest("alice@example.com", "Secret123!", "Alice", "UTC");
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123!")).thenReturn("hashed-pwd");
        when(jwtService.generateToken(any(User.class))).thenReturn("jwt-token");

        AuthResponse response = authService.register(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("alice@example.com");
        assertThat(savedUser.getPasswordHash()).isEqualTo("hashed-pwd");
        assertThat(savedUser.getDisplayName()).isEqualTo("Alice");
        assertThat(savedUser.getTimeZone()).isEqualTo("UTC");
        assertThat(savedUser.isSkillsRegistered()).isFalse();
    }

    @Test
    @DisplayName("Register failure: duplicate email throws 409 Conflict")
    void shouldThrowConflictWhenEmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest("existing@example.com", "Password", "User", "UTC");
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(apiEx.getCode()).isEqualTo("EMAIL_ALREADY_REGISTERED");
                });

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Login success: returns JWT for valid credentials")
    void shouldLoginWithValidCredentials() {
        LoginRequest request = new LoginRequest("alice@example.com", "Password123!");
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("alice@example.com")
                .passwordHash("hashed-pwd")
                .build();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", "hashed-pwd")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("valid-jwt");

        AuthResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("valid-jwt");
    }

    @Test
    @DisplayName("Login failure: unknown email throws 401 Unauthorized")
    void shouldThrowUnauthorizedWhenEmailNotFound() {
        LoginRequest request = new LoginRequest("unknown@example.com", "pass");
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(apiEx.getCode()).isEqualTo("INVALID_CREDENTIALS");
                });
    }

    @Test
    @DisplayName("Login failure: wrong password throws 401 Unauthorized")
    void shouldThrowUnauthorizedWhenPasswordIncorrect() {
        LoginRequest request = new LoginRequest("alice@example.com", "wrong-password");
        User user = User.builder().passwordHash("correct-hash").build();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "correct-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }

    @Test
    @DisplayName("Login failure: account with null passwordHash (OAuth only) throws 401 Unauthorized")
    void shouldThrowUnauthorizedWhenPasswordHashIsNull() {
        LoginRequest request = new LoginRequest("google-only@example.com", "any-password");
        User user = User.builder().passwordHash(null).build();
        when(userRepository.findByEmail("google-only@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }

    @Test
    @DisplayName("Google OAuth: existing user by googleSubject returns directly")
    void shouldReturnExistingUserByGoogleSubject() {
        User existing = User.builder().googleSubject("sub-123").email("g@example.com").build();
        when(userRepository.findByGoogleSubject("sub-123")).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        User result = authService.findOrCreateGoogleUser("sub-123", "g@example.com", "G User");

        assertThat(result).isSameAs(existing);
    }

    @Test
    @DisplayName("Google OAuth: links googleSubject to existing local account by email")
    void shouldLinkGoogleSubjectToExistingEmailAccount() {
        User localUser = User.builder().email("shared@example.com").googleSubject(null).build();
        when(userRepository.findByGoogleSubject("sub-456")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("shared@example.com")).thenReturn(Optional.of(localUser));
        when(userRepository.save(localUser)).thenReturn(localUser);

        User result = authService.findOrCreateGoogleUser("sub-456", "shared@example.com", "Display");

        assertThat(result.getGoogleSubject()).isEqualTo("sub-456");
    }

    @Test
    @DisplayName("Google OAuth: creates new user if neither subject nor email exists")
    void shouldCreateNewUserWhenGoogleAccountNotSeenBefore() {
        when(userRepository.findByGoogleSubject("new-sub")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = authService.findOrCreateGoogleUser("new-sub", "new@gmail.com", "New User");

        assertThat(result.getEmail()).isEqualTo("new@gmail.com");
        assertThat(result.getGoogleSubject()).isEqualTo("new-sub");
        assertThat(result.getDisplayName()).isEqualTo("New User");
        assertThat(result.getTimeZone()).isEqualTo("UTC");
        assertThat(result.isSkillsRegistered()).isFalse();
    }

    @Test
    @DisplayName("Current user: returns MeResponse for existing user")
    void shouldReturnMeResponseForExistingUser() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("me@example.com")
                .displayName("Me")
                .bio("Developer")
                .timeZone("America/Sao_Paulo")
                .skillsRegistered(true)
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        MeResponse response = authService.currentUser(userId);

        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo("me@example.com");
        assertThat(response.displayName()).isEqualTo("Me");
        assertThat(response.bio()).isEqualTo("Developer");
        assertThat(response.timeZone()).isEqualTo("America/Sao_Paulo");
        assertThat(response.skillsRegistered()).isTrue();
    }

    @Test
    @DisplayName("Current user: throws 404 when user not found")
    void shouldThrowNotFoundWhenUserDoesNotExist() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.currentUser(userId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getCode()).isEqualTo("USER_NOT_FOUND");
                });
    }
}
