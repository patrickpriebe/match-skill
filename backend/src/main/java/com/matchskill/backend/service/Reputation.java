package com.matchskill.backend.service;

/** Average rating and count over a user's received, COMPLETED-exchange feedback. */
public record Reputation(double average, long count) {

    public static final Reputation NONE = new Reputation(0.0, 0L);
}
