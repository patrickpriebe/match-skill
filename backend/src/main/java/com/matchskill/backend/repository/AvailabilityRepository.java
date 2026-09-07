package com.matchskill.backend.repository;

import com.matchskill.backend.entity.Availability;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilityRepository extends JpaRepository<Availability, UUID> {

    List<Availability> findByUserId(UUID userId);

    List<Availability> findByUserIdIn(Collection<UUID> userIds);

    void deleteByUserId(UUID userId);
}
