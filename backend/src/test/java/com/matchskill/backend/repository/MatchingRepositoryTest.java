package com.matchskill.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.matchskill.backend.entity.Exchange;
import com.matchskill.backend.entity.ExchangeStatus;
import com.matchskill.backend.entity.ExchangeStrength;
import com.matchskill.backend.entity.Feedback;
import com.matchskill.backend.entity.Skill;
import com.matchskill.backend.entity.SkillDirection;
import com.matchskill.backend.entity.SkillStatus;
import com.matchskill.backend.entity.User;
import com.matchskill.backend.entity.UserSkill;
import com.matchskill.backend.service.Reputation;
import com.matchskill.backend.service.ReputationService;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@Import(ReputationService.class)
class MatchingRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserSkillRepository userSkillRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private ReputationService reputationService;

    @Test
    void shouldAggregatePublishedParticipantRatingsInOneQueryWithoutLoadingEntities() {
        User first = user("First");
        User second = user("Second");
        User third = user("Third");
        User unrated = user("Unrated");
        Skill java = skill("Java", SkillStatus.APPROVED);

        Exchange firstExchange = exchange(first, second, java, ExchangeStatus.COMPLETED);
        feedback(firstExchange, second, 5);
        feedback(firstExchange, first, 2);
        feedback(firstExchange, third, 1); // Historical corrupt author must never contribute.
        Exchange secondExchange = exchange(third, first, java, ExchangeStatus.COMPLETED);
        feedback(secondExchange, third, 4);
        feedback(secondExchange, first, 3);

        Exchange incomplete = exchange(first, second, java, ExchangeStatus.SCHEDULED);
        feedback(incomplete, first, 1);
        feedback(incomplete, second, 1);
        Exchange privateExchange = exchange(first, third, java, ExchangeStatus.COMPLETED);
        feedback(privateExchange, third, 1);
        Exchange invalidSelfExchange = exchange(first, first, java, ExchangeStatus.COMPLETED);
        feedback(invalidSelfExchange, first, 1);
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = statistics();
        statistics.clear();

        Map<UUID, Reputation> reputations =
                reputationService.ofBatch(Set.of(first.getId(), second.getId(), third.getId(), unrated.getId()));

        assertThat(reputations).containsEntry(first.getId(), new Reputation(4.5, 2))
                .containsEntry(second.getId(), new Reputation(2, 1))
                .containsEntry(third.getId(), new Reputation(3, 1))
                .containsEntry(unrated.getId(), Reputation.NONE);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(statistics.getEntityLoadCount()).isZero();
        assertThat(reputationService.of(first.getId())).isEqualTo(reputations.get(first.getId()));
        assertThat(feedbackRepository.findReceivedByUserId(first.getId()))
                .extracting(Feedback::getRating).containsExactlyInAnyOrder(5, 4);
    }

    @Test
    void shouldNotPublishPrivateRatingWhenOnlyAnOutsiderAlsoSubmitted() {
        User requester = user("Requester");
        User receiver = user("Receiver");
        User outsider = user("Outsider");
        Exchange exchange = exchange(requester, receiver, skill("Java", SkillStatus.APPROVED), ExchangeStatus.COMPLETED);
        Feedback privateFeedback = feedback(exchange, receiver, 5);
        feedback(exchange, outsider, 1);
        entityManager.flush();
        entityManager.clear();

        assertThat(reputationService.ofBatch(Set.of(requester.getId(), receiver.getId(), outsider.getId())))
                .containsEntry(requester.getId(), Reputation.NONE)
                .containsEntry(receiver.getId(), Reputation.NONE)
                .containsEntry(outsider.getId(), Reputation.NONE);
        assertThat(feedbackRepository.findReceivedByUserId(requester.getId())).isEmpty();
        assertThat(feedbackRepository.findReceivedForViewer(requester.getId(), requester.getId())).isEmpty();
        assertThat(feedbackRepository.findReceivedForViewer(requester.getId(), outsider.getId())).isEmpty();
        assertThat(feedbackRepository.findReceivedForViewer(requester.getId(), receiver.getId()))
                .extracting(Feedback::getId).containsExactly(privateFeedback.getId());
    }

    @Test
    void shouldFilterApprovedMatchingReadsAndPreserveReclassifiedProfileSkills() {
        User first = user("First");
        User second = user("Second");
        Skill approved = skill("Approved", SkillStatus.APPROVED);
        Skill pending = skill("Pending", SkillStatus.PENDING_REVIEW);
        assignment(first, approved, SkillDirection.OFFERED);
        assignment(first, pending, SkillDirection.OFFERED);
        assignment(first, approved, SkillDirection.WANTED);
        assignment(first, pending, SkillDirection.WANTED);
        assignment(second, pending, SkillDirection.OFFERED);
        entityManager.flush();
        entityManager.clear();

        assertThat(userSkillRepository.findApprovedByUserIdAndDirection(first.getId(), SkillDirection.OFFERED))
                .extracting(entry -> entry.getSkill().getId()).containsExactly(approved.getId());
        assertThat(userSkillRepository.findApprovedBySkillIdInAndDirection(
                Set.of(approved.getId(), pending.getId()), SkillDirection.OFFERED))
                .extracting(entry -> entry.getUser().getId()).containsExactly(first.getId());
        assertThat(userSkillRepository.findApprovedByUserIdInAndDirection(
                Set.of(first.getId(), second.getId()), SkillDirection.WANTED))
                .extracting(entry -> entry.getSkill().getId()).containsExactly(approved.getId());

        entityManager.find(Skill.class, approved.getId()).setStatus(SkillStatus.PENDING_REVIEW);
        entityManager.flush();
        entityManager.clear();
        assertThat(userSkillRepository.findApprovedByUserIdAndDirection(first.getId(), SkillDirection.OFFERED)).isEmpty();
        assertThat(userSkillRepository.findApprovedBySkillIdInAndDirection(
                Set.of(approved.getId(), pending.getId()), SkillDirection.OFFERED)).isEmpty();
        assertThat(userSkillRepository.findApprovedByUserIdInAndDirection(
                Set.of(first.getId(), second.getId()), SkillDirection.WANTED)).isEmpty();
        assertThat(userSkillRepository.findByUserIdAndDirection(first.getId(), SkillDirection.OFFERED)).hasSize(2);
    }

    @Test
    void shouldFetchProfileSkillMetadataInOneQueryAndAllowDetachedMapping() {
        User user = user("Profile");
        assignment(user, skill("Java", SkillStatus.APPROVED), SkillDirection.OFFERED);
        assignment(user, skill("React", SkillStatus.APPROVED), SkillDirection.OFFERED);
        assignment(user, skill("Pending", SkillStatus.PENDING_REVIEW), SkillDirection.OFFERED);
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = statistics();
        statistics.clear();

        List<UserSkill> entries = userSkillRepository.findByUserIdAndDirection(user.getId(), SkillDirection.OFFERED);
        entityManager.clear();

        assertThat(entries).extracting(entry -> entry.getSkill().getName())
                .containsExactlyInAnyOrder("Java", "React", "Pending");
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    private Statistics statistics() {
        return entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    }

    private User user(String name) {
        User user = User.builder().email(name.toLowerCase() + "@example.test")
                .displayName(name).timeZone("UTC").build();
        entityManager.persist(user);
        return user;
    }

    private Skill skill(String name, SkillStatus status) {
        Skill skill = Skill.builder().name(name).slug(name.toLowerCase()).status(status).build();
        entityManager.persist(skill);
        return skill;
    }

    private Exchange exchange(User requester, User receiver, Skill skill, ExchangeStatus status) {
        Exchange exchange = Exchange.builder().requester(requester).receiver(receiver)
                .skillFromReceiver(skill).strength(ExchangeStrength.PARTIAL).status(status).build();
        entityManager.persist(exchange);
        return exchange;
    }

    private Feedback feedback(Exchange exchange, User author, int rating) {
        Feedback feedback = Feedback.builder().exchange(exchange).author(author).rating(rating).build();
        entityManager.persist(feedback);
        return feedback;
    }

    private void assignment(User user, Skill skill, SkillDirection direction) {
        entityManager.persist(UserSkill.builder().user(user).skill(skill).direction(direction).build());
    }
}
