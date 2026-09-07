package com.matchskill.backend.entity;

import com.matchskill.backend.validation.MeetingUrlConstraints;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * Each exchange records which skill travels in each direction, so a trade is
 * always legible as "A taught X, B taught Y". On a PARTIAL request only
 * skillFromReceiver is known when the invitation is sent — skillFromRequester
 * is chosen by the receiver at acceptance, from the requester's offered list.
 */
@Entity
@Table(name = "exchanges", indexes = {
        @Index(name = "idx_exchanges_requester_status_created", columnList = "requester_id,status,created_at"),
        @Index(name = "idx_exchanges_receiver_status_created", columnList = "receiver_id,status,created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Exchange {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    /** What the requester wants to learn — set at request time. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_from_receiver_id", nullable = false)
    private Skill skillFromReceiver;

    /** What the receiver will learn — null until acceptance on a PARTIAL. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_from_requester_id")
    private Skill skillFromRequester;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExchangeStrength strength;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExchangeStatus status;

    /** Absolute instant; null before SCHEDULED. */
    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    /** Allowlist-validated; null before SCHEDULED. */
    @Column(name = "meeting_url", length = MeetingUrlConstraints.MAX_LENGTH)
    private String meetingUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
