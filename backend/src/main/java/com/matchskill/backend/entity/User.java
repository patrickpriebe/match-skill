package com.matchskill.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    /** Null for accounts created through Google only. */
    @Column(name = "password_hash")
    private String passwordHash;

    /** Google's stable user id. Null for local-only accounts. */
    @Column(name = "google_subject", unique = true)
    private String googleSubject;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(columnDefinition = "text")
    private String bio;

    /** IANA name, e.g. America/Sao_Paulo. */
    @Column(name = "time_zone", nullable = false)
    private String timeZone;

    @Column(name = "skills_registered", nullable = false)
    @Builder.Default
    private boolean skillsRegistered = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
