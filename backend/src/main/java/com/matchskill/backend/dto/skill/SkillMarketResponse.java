package com.matchskill.backend.dto.skill;

import java.util.List;

/**
 * What the shared vocabulary looks like around one person's own skills: for
 * each one, how many other people teach it and how many want it.
 *
 * <p>The vocabulary is normalised and global, so these counts are cheap and
 * exact — and they answer a question the product could never answer before:
 * which of the things I can teach is actually worth something here. Someone
 * whose Java sits behind eighteen other teachers and whose Libras is the only
 * one on the platform learns more from two numbers than from any ranking.
 */
public record SkillMarketResponse(List<SkillStanding> offered, List<SkillStanding> wanted) {

    /** One skill's standing. {@code teachers} and {@code learners} exclude the asking user. */
    public record SkillStanding(SkillResponse skill, long teachers, long learners) {}
}
