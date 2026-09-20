package com.matchskill.backend.dto.ring;

import java.util.List;

/**
 * A closed chain of teaching in which nobody had to want what their own
 * student could teach. The asking user is always the first member.
 *
 * <p>{@code weakestLinkMinutes} is the smallest weekly overlap among the
 * consecutive pairs, not the average: a ring is only as schedulable as its
 * worst pair, and averaging would let two easy pairs hide one impossible one.
 */
public record RingResponse(List<RingMemberResponse> members, int weakestLinkMinutes) {}
