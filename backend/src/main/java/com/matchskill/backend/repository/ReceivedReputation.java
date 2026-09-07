package com.matchskill.backend.repository;

import java.util.UUID;

/** Database aggregate of feedback received from the other exchange participant. */
public interface ReceivedReputation {

    UUID getUserId();

    double getAverageRating();

    long getRatingCount();
}
