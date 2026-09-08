package com.matchskill.backend.service;

import com.matchskill.backend.entity.Skill;
import com.matchskill.backend.entity.SkillStatus;
import com.matchskill.backend.exception.ApiException;
import com.matchskill.backend.repository.SkillRepository;
import com.matchskill.backend.util.Slugs;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
public class SkillService {

    private final SkillRepository skillRepository;

    public SkillService(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
    }

    @Transactional(readOnly = true)
    public Page<Skill> search(String query, Pageable pageable) {
        return skillRepository.findByStatusAndNameContainingIgnoreCase(
                SkillStatus.APPROVED, query == null ? "" : query, pageable);
    }

    /**
     * Keep unique-key retries outside the repository's failed transaction. Suspending
     * any caller transaction lets a competing insert commit before the next lookup.
     * Public legacy IDs, slugs, names and status never change when claiming identity.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Skill suggest(String name) {
        if (name == null || name.length() > 100 || Slugs.normalizedIdentity(name).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SKILL_NAME",
                    "Skill name must contain a letter or digit and be at most 100 characters");
        }
        String displayName = name.strip();
        String identity = Slugs.normalizedIdentity(displayName);
        String identityKey = Slugs.identityKey(displayName);
        String preferredSlug = Slugs.slugify(displayName);
        String fallbackSlug = Slugs.fallbackSlug(displayName);
        Set<String> lookupSlugs = new LinkedHashSet<>(List.of(
                preferredSlug, fallbackSlug, Slugs.legacySlug(displayName)));
        for (int attempt = 0; attempt < 3; attempt++) {
            List<Skill> candidates = skillRepository.findIdentityCandidates(identityKey, lookupSlugs, displayName);
            List<Skill> matches = candidates.stream()
                    .filter(skill -> Slugs.normalizedIdentity(skill.getName()).equals(identity)).toList();
            if (matches.size() > 1 || candidates.stream().anyMatch(skill ->
                    identityKey.equals(skill.getIdentityKey())
                            && !Slugs.normalizedIdentity(skill.getName()).equals(identity))) {
                throw identityConflict();
            }
            Skill result;
            if (!matches.isEmpty()) {
                result = matches.getFirst();
                if (identityKey.equals(result.getIdentityKey())) {
                    return result;
                }
                if (result.getIdentityKey() != null) {
                    throw identityConflict();
                }
                result.setIdentityKey(identityKey);
            } else {
                boolean preferredOccupied = candidates.stream().anyMatch(skill -> preferredSlug.equals(skill.getSlug()));
                String slug = preferredOccupied ? fallbackSlug : preferredSlug;
                if (candidates.stream().anyMatch(skill -> slug.equals(skill.getSlug()))) {
                    throw identityConflict();
                }
                result = Skill.builder().name(displayName).slug(slug).identityKey(identityKey)
                        .status(SkillStatus.APPROVED).build();
            }
            try {
                return skillRepository.saveAndFlush(result);
            } catch (DataIntegrityViolationException conflict) {
                // The repository transaction has rolled back. Re-resolve the committed winner.
                if (attempt == 2) {
                    throw identityConflict();
                }
            }
        }
        throw identityConflict();
    }

    private ApiException identityConflict() {
        return new ApiException(HttpStatus.CONFLICT, "SKILL_IDENTITY_CONFLICT",
                "Skill identity conflicts with the vocabulary; review existing entries before retrying");
    }
}
