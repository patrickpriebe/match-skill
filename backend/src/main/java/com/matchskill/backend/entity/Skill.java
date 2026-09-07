package com.matchskill.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/** The controlled vocabulary. Users select from it; they do not create rows. */
@Entity
@Table(name = "skills")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Skill {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    /** Canonical display name. */
    @Column(nullable = false, unique = true)
    private String name;

    /** Normalized key used for matching. */
    @Column(nullable = false, unique = true)
    private String slug;

    /** Internal normalized identity; null on legacy rows until an unambiguous claim. */
    @Column(name = "identity_key", length = 64, unique = true)
    private String identityKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SkillStatus status;
}
