package com.matchskill.backend.repository;

import com.matchskill.backend.entity.SkillDirection;
import java.util.UUID;

/**
 * How many distinct people declared one skill in one direction. Two rows per
 * skill at most: one for those who teach it, one for those who want it.
 */
public interface SkillDemand {

    UUID getSkillId();

    SkillDirection getDirection();

    long getPeople();
}
