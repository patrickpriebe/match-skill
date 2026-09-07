package com.matchskill.backend.repository;

import com.matchskill.backend.entity.Skill;
import com.matchskill.backend.entity.SkillStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SkillRepository extends JpaRepository<Skill, UUID> {

    Optional<Skill> findBySlug(String slug);

    @Query("select s from Skill s where s.identityKey = :identityKey or s.slug in :slugs "
            + "or lower(s.name) = lower(:name)")
    List<Skill> findIdentityCandidates(@Param("identityKey") String identityKey,
            @Param("slugs") Collection<String> slugs, @Param("name") String name);

    Page<Skill> findByStatusAndNameContainingIgnoreCase(
            SkillStatus status, String name, Pageable pageable);
}
