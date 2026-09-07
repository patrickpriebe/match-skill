package com.matchskill.backend.repository;

import com.matchskill.backend.entity.SkillDirection;
import com.matchskill.backend.entity.UserSkill;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSkillRepository extends JpaRepository<UserSkill, UUID> {

    @EntityGraph(attributePaths = "skill")
    List<UserSkill> findByUserId(UUID userId);

    @EntityGraph(attributePaths = "skill")
    List<UserSkill> findByUserIdAndDirection(UUID userId, SkillDirection direction);

    List<UserSkill> findBySkillIdInAndDirection(Collection<UUID> skillIds, SkillDirection direction);

    List<UserSkill> findByUserIdInAndDirection(Collection<UUID> userIds, SkillDirection direction);

    @Query("select us from UserSkill us join fetch us.skill s "
            + "where us.user.id = :userId and us.direction = :direction "
            + "and s.status = com.matchskill.backend.entity.SkillStatus.APPROVED")
    List<UserSkill> findApprovedByUserIdAndDirection(
            @Param("userId") UUID userId, @Param("direction") SkillDirection direction);

    @Query("select us from UserSkill us join fetch us.skill s "
            + "where s.id in :skillIds and us.direction = :direction "
            + "and s.status = com.matchskill.backend.entity.SkillStatus.APPROVED")
    List<UserSkill> findApprovedBySkillIdInAndDirection(
            @Param("skillIds") Collection<UUID> skillIds, @Param("direction") SkillDirection direction);

    @Query("select us from UserSkill us join fetch us.skill s "
            + "where us.user.id in :userIds and us.direction = :direction "
            + "and s.status = com.matchskill.backend.entity.SkillStatus.APPROVED")
    List<UserSkill> findApprovedByUserIdInAndDirection(
            @Param("userIds") Collection<UUID> userIds, @Param("direction") SkillDirection direction);

    void deleteByUserId(UUID userId);
}
