package com.matchskill.backend.dto.auth;

import com.matchskill.backend.validation.ValidTimeZone;
import com.matchskill.backend.validation.ValidPasswordLength;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, message = "must be at least 8 characters")
                @ValidPasswordLength
                @Pattern(regexp = "(?s)(?=.*\\p{Lu})(?=.*\\p{Ll})(?=.*\\p{Nd})(?=.*[^\\p{L}\\p{N}]).+",
                        message = "must contain uppercase, lowercase, a number and a symbol") String password,
        @NotBlank @Size(max = 255) String displayName,
        @NotBlank @ValidTimeZone @Size(max = 255) String timeZone) {}
