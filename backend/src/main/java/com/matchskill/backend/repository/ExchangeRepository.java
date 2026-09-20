package com.matchskill.backend.repository;

import com.matchskill.backend.entity.Exchange;
import com.matchskill.backend.entity.ExchangeStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExchangeRepository extends JpaRepository<Exchange, UUID> {

    @EntityGraph(attributePaths = {"skillFromReceiver", "skillFromRequester"})
    @Query(
            "select e from Exchange e "
                    + "where (e.requester.id = :userId or e.receiver.id = :userId) "
                    + "and (:status is null or e.status = :status)")
    Page<Exchange> findByParticipant(
            @Param("userId") UUID userId, @Param("status") ExchangeStatus status, Pageable pageable);

    /**
     * The still-open exchanges between two people, newest first.
     *
     * <p>The browser used to answer this by paging through the whole history
     * fifty rows at a time and filtering in JavaScript — an unbounded number of
     * requests to find out whether one row exists. The database can answer it
     * with an index seek.
     */
    @EntityGraph(attributePaths = {"skillFromReceiver", "skillFromRequester"})
    @Query(
            "select e from Exchange e "
                    + "where ((e.requester.id = :userId and e.receiver.id = :otherId) "
                    + "   or (e.requester.id = :otherId and e.receiver.id = :userId)) "
                    + "and e.status in :statuses "
                    + "order by e.createdAt desc, e.id desc")
    List<Exchange> findBetweenWithStatusIn(
            @Param("userId") UUID userId,
            @Param("otherId") UUID otherId,
            @Param("statuses") Collection<ExchangeStatus> statuses,
            Pageable pageable);

    // Lock only the exchange row; nullable skill joins cannot be locked safely on PostgreSQL.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Exchange e where e.id = :id")
    Optional<Exchange> findByIdForUpdate(@Param("id") UUID id);
}
