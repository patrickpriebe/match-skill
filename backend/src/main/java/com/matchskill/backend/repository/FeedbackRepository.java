package com.matchskill.backend.repository;

import com.matchskill.backend.entity.Feedback;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {

    List<Feedback> findByExchangeId(UUID exchangeId);

    boolean existsByExchangeIdAndAuthorId(UUID exchangeId, UUID authorId);

    /**
     * Feedback RECEIVED by a user: rows authored by the other participant of
     * a completed exchange this user took part in, once both reviews are submitted.
     */
    @Query(
            "select f from Feedback f join f.exchange e "
                    + "where e.status = com.matchskill.backend.entity.ExchangeStatus.COMPLETED "
                    + "and f.author.id <> :userId "
                    + "and ((e.requester.id = :userId and f.author.id = e.receiver.id) "
                    + "or (e.receiver.id = :userId and f.author.id = e.requester.id)) "
                    + "and exists (select counterpart.id from Feedback counterpart "
                    + "where counterpart.exchange.id = e.id and counterpart.author.id = :userId)")
    List<Feedback> findReceivedByUserId(@Param("userId") UUID userId);

    /** Public received feedback plus the viewer's own still-private submission. */
    @Query(
            "select f from Feedback f join f.exchange e "
                    + "where e.status = com.matchskill.backend.entity.ExchangeStatus.COMPLETED "
                    + "and f.author.id <> :userId "
                    + "and ((e.requester.id = :userId and f.author.id = e.receiver.id) "
                    + "or (e.receiver.id = :userId and f.author.id = e.requester.id)) "
                    + "and (f.author.id = :viewerId or exists (select counterpart.id from Feedback counterpart "
                    + "where counterpart.exchange.id = e.id and counterpart.author.id = :userId))")
    List<Feedback> findReceivedForViewer(@Param("userId") UUID userId, @Param("viewerId") UUID viewerId);

    /**
     * Aggregate in the database so feedback volume does not create entity loads or
     * lazy exchange lookups. Only jointly published reviews contribute to reputation;
     * each valid feedback row belongs to exactly one recipient.
     */
    @Query(
            "select case when f.author.id = e.requester.id then e.receiver.id else e.requester.id end as userId, "
                    + "avg(f.rating) as averageRating, count(f.id) as ratingCount "
                    + "from Feedback f join f.exchange e "
                    + "where e.status = com.matchskill.backend.entity.ExchangeStatus.COMPLETED "
                    + "and e.requester.id <> e.receiver.id "
                    + "and ((e.requester.id in :userIds and f.author.id = e.receiver.id) "
                    + "or (e.receiver.id in :userIds and f.author.id = e.requester.id)) "
                    + "and exists (select counterpart.id from Feedback counterpart "
                    + "where counterpart.exchange.id = e.id and counterpart.author.id <> f.author.id "
                    + "and (counterpart.author.id = e.requester.id or counterpart.author.id = e.receiver.id)) "
                    + "group by case when f.author.id = e.requester.id then e.receiver.id else e.requester.id end")
    List<ReceivedReputation> aggregateReceivedByUserIds(@Param("userIds") Collection<UUID> userIds);
}
