package com.matchskill.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.matchskill.backend.dto.exchange.ExchangeResponse;
import com.matchskill.backend.entity.Exchange;
import com.matchskill.backend.entity.ExchangeStatus;
import com.matchskill.backend.entity.ExchangeStrength;
import com.matchskill.backend.entity.Skill;
import com.matchskill.backend.entity.SkillDirection;
import com.matchskill.backend.entity.User;
import com.matchskill.backend.entity.UserSkill;
import com.matchskill.backend.exception.ApiException;
import com.matchskill.backend.repository.ExchangeRepository;
import com.matchskill.backend.repository.SkillRepository;
import com.matchskill.backend.repository.UserRepository;
import com.matchskill.backend.repository.UserSkillRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ExchangeServiceTest {

    @Mock
    private ExchangeRepository exchangeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private UserSkillRepository userSkillRepository;

    @Mock
    private MeetingUrlValidator meetingUrlValidator;

    @InjectMocks
    private ExchangeService exchangeService;

    private User requester;
    private User receiver;
    private User stranger;
    private Skill skill1;
    private Skill skill2;

    @BeforeEach
    void setUp() {
        requester = User.builder().id(UUID.randomUUID()).displayName("Requester").build();
        receiver = User.builder().id(UUID.randomUUID()).displayName("Receiver").build();
        stranger = User.builder().id(UUID.randomUUID()).displayName("Stranger").build();
        skill1 = Skill.builder().id(UUID.randomUUID()).name("Java").build();
        skill2 = Skill.builder().id(UUID.randomUUID()).name("React").build();
    }

    @Test
    @DisplayName("Create exchange: prevents requesting with oneself")
    void shouldPreventSelfExchange() {
        UUID sameId = UUID.randomUUID();
        assertThatThrownBy(() -> exchangeService.create(sameId, sameId, UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getCode()).isEqualTo("CANNOT_EXCHANGE_WITH_SELF");
                });
    }

    @Test
    @DisplayName("Create exchange: throws 400 when receiver does not offer chosen skill")
    void shouldThrowWhenReceiverDoesNotOfferSkill() {
        when(userRepository.findById(requester.getId())).thenReturn(Optional.of(requester));
        when(userRepository.findById(receiver.getId())).thenReturn(Optional.of(receiver));
        when(skillRepository.findById(skill1.getId())).thenReturn(Optional.of(skill1));

        // Receiver offers empty
        when(userSkillRepository.findByUserIdAndDirection(receiver.getId(), SkillDirection.OFFERED))
                .thenReturn(List.of());

        assertThatThrownBy(() -> exchangeService.create(requester.getId(), receiver.getId(), skill1.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getCode()).isEqualTo("SKILL_NOT_OFFERED");
                });
    }

    @Test
    @DisplayName("Create exchange: sets strength MUTUAL when requester offers what receiver wants")
    void shouldCreateMutualExchange() {
        when(userRepository.findById(requester.getId())).thenReturn(Optional.of(requester));
        when(userRepository.findById(receiver.getId())).thenReturn(Optional.of(receiver));
        when(skillRepository.findById(skill1.getId())).thenReturn(Optional.of(skill1));

        // Receiver offers skill1
        when(userSkillRepository.findByUserIdAndDirection(receiver.getId(), SkillDirection.OFFERED))
                .thenReturn(List.of(UserSkill.builder().user(receiver).skill(skill1).build()));

        // Requester offers skill2, Receiver wants skill2 (MUTUAL)
        when(userSkillRepository.findByUserIdAndDirection(requester.getId(), SkillDirection.OFFERED))
                .thenReturn(List.of(UserSkill.builder().user(requester).skill(skill2).build()));
        when(userSkillRepository.findByUserIdAndDirection(receiver.getId(), SkillDirection.WANTED))
                .thenReturn(List.of(UserSkill.builder().user(receiver).skill(skill2).build()));

        when(exchangeRepository.saveAndFlush(any(Exchange.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExchangeResponse result = exchangeService.create(requester.getId(), receiver.getId(), skill1.getId());

        assertThat(result.strength()).isEqualTo(ExchangeStrength.MUTUAL);
        assertThat(result.status()).isEqualTo(ExchangeStatus.REQUESTED);
        assertThat(result.skillFromReceiver().id()).isEqualTo(skill1.getId());
    }

    @Test
    @DisplayName("Create exchange: sets strength PARTIAL when receiver does not want requester's offered skills")
    void shouldCreatePartialExchange() {
        when(userRepository.findById(requester.getId())).thenReturn(Optional.of(requester));
        when(userRepository.findById(receiver.getId())).thenReturn(Optional.of(receiver));
        when(skillRepository.findById(skill1.getId())).thenReturn(Optional.of(skill1));

        when(userSkillRepository.findByUserIdAndDirection(receiver.getId(), SkillDirection.OFFERED))
                .thenReturn(List.of(UserSkill.builder().user(receiver).skill(skill1).build()));

        // Requester offers skill1, but receiver wants skill2 (no match for receiver)
        when(userSkillRepository.findByUserIdAndDirection(requester.getId(), SkillDirection.OFFERED))
                .thenReturn(List.of(UserSkill.builder().user(requester).skill(skill1).build()));
        when(userSkillRepository.findByUserIdAndDirection(receiver.getId(), SkillDirection.WANTED))
                .thenReturn(List.of(UserSkill.builder().user(receiver).skill(skill2).build()));

        when(exchangeRepository.saveAndFlush(any(Exchange.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExchangeResponse result = exchangeService.create(requester.getId(), receiver.getId(), skill1.getId());

        assertThat(result.strength()).isEqualTo(ExchangeStrength.PARTIAL);
        assertThat(result.status()).isEqualTo(ExchangeStatus.REQUESTED);
    }

    @Test
    @DisplayName("Get exchange: throws 403 when user is not participant")
    void shouldThrowForbiddenWhenUserIsNotParticipant() {
        Exchange exchange = Exchange.builder().requester(requester).receiver(receiver)
                .skillFromReceiver(skill1).build();
        when(exchangeRepository.findById(exchange.getId())).thenReturn(Optional.of(exchange));

        assertThatThrownBy(() -> exchangeService.get(stranger.getId(), exchange.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.getCode()).isEqualTo("NOT_A_PARTICIPANT");
                });
    }

    @Test
    @DisplayName("Accept exchange: only receiver can accept, requires REQUESTED status")
    void shouldAcceptExchangeByReceiver() {
        UUID exchangeId = UUID.randomUUID();
        Exchange exchange = Exchange.builder()
                .id(exchangeId)
                .requester(requester)
                .receiver(receiver)
                .skillFromReceiver(skill1)
                .status(ExchangeStatus.REQUESTED)
                .build();
        when(exchangeRepository.findByIdForUpdate(exchangeId)).thenReturn(Optional.of(exchange));
        when(userSkillRepository.findByUserIdAndDirection(requester.getId(), SkillDirection.OFFERED))
                .thenReturn(List.of(UserSkill.builder().user(requester).skill(skill2).build()));
        when(skillRepository.findById(skill2.getId())).thenReturn(Optional.of(skill2));
        when(exchangeRepository.saveAndFlush(any(Exchange.class))).thenAnswer(inv -> inv.getArgument(0));

        ExchangeResponse accepted = exchangeService.accept(receiver.getId(), exchangeId, skill2.getId());

        assertThat(accepted.status()).isEqualTo(ExchangeStatus.ACCEPTED);
        assertThat(accepted.skillFromRequester().id()).isEqualTo(skill2.getId());
    }

    @Test
    @DisplayName("Accept exchange: throws 403 if requester or stranger attempts to accept")
    void shouldThrowForbiddenWhenNonReceiverAttemptsAccept() {
        UUID exchangeId = UUID.randomUUID();
        Exchange exchange = Exchange.builder()
                .id(exchangeId)
                .requester(requester)
                .receiver(receiver)
                .skillFromReceiver(skill1)
                .status(ExchangeStatus.REQUESTED)
                .build();
        when(exchangeRepository.findByIdForUpdate(exchangeId)).thenReturn(Optional.of(exchange));

        assertThatThrownBy(() -> exchangeService.accept(requester.getId(), exchangeId, skill2.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.getCode()).isEqualTo("ONLY_RECEIVER_ALLOWED");
                });
    }

    @ParameterizedTest(name = "Cannot accept from status: {0}")
    @EnumSource(value = ExchangeStatus.class, names = {"ACCEPTED", "DECLINED", "SCHEDULED", "COMPLETED", "CANCELLED"})
    void shouldRejectAcceptWhenNotInRequestedStatus(ExchangeStatus nonRequestedStatus) {
        UUID exchangeId = UUID.randomUUID();
        Exchange exchange = Exchange.builder()
                .id(exchangeId)
                .requester(requester)
                .receiver(receiver)
                .skillFromReceiver(skill1)
                .status(nonRequestedStatus)
                .build();
        when(exchangeRepository.findByIdForUpdate(exchangeId)).thenReturn(Optional.of(exchange));

        assertThatThrownBy(() -> exchangeService.accept(receiver.getId(), exchangeId, skill2.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(apiEx.getCode()).isEqualTo("INVALID_STATE_TRANSITION");
                });
    }

    @Test
    @DisplayName("Decline exchange: receiver can decline REQUESTED exchange")
    void shouldDeclineExchange() {
        UUID exchangeId = UUID.randomUUID();
        Exchange exchange = Exchange.builder()
                .id(exchangeId)
                .requester(requester)
                .receiver(receiver)
                .skillFromReceiver(skill1)
                .status(ExchangeStatus.REQUESTED)
                .build();
        when(exchangeRepository.findByIdForUpdate(exchangeId)).thenReturn(Optional.of(exchange));
        when(exchangeRepository.saveAndFlush(any(Exchange.class))).thenAnswer(inv -> inv.getArgument(0));

        ExchangeResponse declined = exchangeService.decline(receiver.getId(), exchangeId);

        assertThat(declined.status()).isEqualTo(ExchangeStatus.DECLINED);
    }

    @Test
    @DisplayName("Schedule exchange: allows either participant, validates meetingUrl, transitions to SCHEDULED")
    void shouldScheduleExchange() {
        UUID exchangeId = UUID.randomUUID();
        Exchange exchange = Exchange.builder()
                .id(exchangeId)
                .requester(requester)
                .receiver(receiver)
                .skillFromReceiver(skill1)
                .status(ExchangeStatus.ACCEPTED)
                .build();
        when(exchangeRepository.findByIdForUpdate(exchangeId)).thenReturn(Optional.of(exchange));
        when(exchangeRepository.saveAndFlush(any(Exchange.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant future = Instant.now().plusSeconds(3600);
        String url = "https://meet.google.com/abc-defg-hij";

        ExchangeResponse scheduled = exchangeService.schedule(requester.getId(), exchangeId, future, url);

        verify(meetingUrlValidator).validate(url);
        assertThat(scheduled.status()).isEqualTo(ExchangeStatus.SCHEDULED);
        assertThat(scheduled.meetingUrl()).isEqualTo(url);
        assertThat(scheduled.scheduledAt()).isEqualTo(future);
    }

    @Test
    @DisplayName("Schedule exchange: throws 409 if status is not ACCEPTED or SCHEDULED")
    void shouldRejectScheduleWhenNotInAcceptedStatus() {
        UUID exchangeId = UUID.randomUUID();
        Exchange exchange = Exchange.builder()
                .id(exchangeId)
                .requester(requester)
                .receiver(receiver)
                .skillFromReceiver(skill1)
                .status(ExchangeStatus.REQUESTED)
                .build();
        when(exchangeRepository.findByIdForUpdate(exchangeId)).thenReturn(Optional.of(exchange));

        assertThatThrownBy(() -> exchangeService.schedule(
                requester.getId(), exchangeId, Instant.now(), "https://zoom.us/j/123"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(apiEx.getCode()).isEqualTo("INVALID_STATE_TRANSITION");
                });
    }

    @Test
    @DisplayName("Complete exchange: marks exchange COMPLETED only from SCHEDULED status")
    void shouldCompleteExchange() {
        UUID exchangeId = UUID.randomUUID();
        Exchange exchange = Exchange.builder()
                .id(exchangeId)
                .requester(requester)
                .receiver(receiver)
                .skillFromReceiver(skill1)
                .status(ExchangeStatus.SCHEDULED)
                .build();
        when(exchangeRepository.findByIdForUpdate(exchangeId)).thenReturn(Optional.of(exchange));
        when(exchangeRepository.saveAndFlush(any(Exchange.class))).thenAnswer(inv -> inv.getArgument(0));

        ExchangeResponse completed = exchangeService.complete(receiver.getId(), exchangeId);

        assertThat(completed.status()).isEqualTo(ExchangeStatus.COMPLETED);
    }

    @ParameterizedTest(name = "Cannot complete from status: {0}")
    @EnumSource(value = ExchangeStatus.class, names = {"REQUESTED", "DECLINED", "ACCEPTED", "CANCELLED", "COMPLETED"})
    void shouldRejectCompleteWhenNotScheduled(ExchangeStatus status) {
        UUID exchangeId = UUID.randomUUID();
        Exchange exchange = Exchange.builder()
                .id(exchangeId)
                .requester(requester)
                .receiver(receiver)
                .skillFromReceiver(skill1)
                .status(status)
                .build();
        when(exchangeRepository.findByIdForUpdate(exchangeId)).thenReturn(Optional.of(exchange));

        assertThatThrownBy(() -> exchangeService.complete(requester.getId(), exchangeId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    @Test
    @DisplayName("Cancel exchange: allows participant to cancel from ACCEPTED or SCHEDULED")
    void shouldCancelExchange() {
        UUID exchangeId = UUID.randomUUID();
        Exchange exchange = Exchange.builder()
                .id(exchangeId)
                .requester(requester)
                .receiver(receiver)
                .skillFromReceiver(skill1)
                .status(ExchangeStatus.SCHEDULED)
                .build();
        when(exchangeRepository.findByIdForUpdate(exchangeId)).thenReturn(Optional.of(exchange));
        when(exchangeRepository.saveAndFlush(any(Exchange.class))).thenAnswer(inv -> inv.getArgument(0));

        ExchangeResponse cancelled = exchangeService.cancel(requester.getId(), exchangeId);

        assertThat(cancelled.status()).isEqualTo(ExchangeStatus.CANCELLED);
    }

    @ParameterizedTest(name = "Cannot cancel from terminal or unaccepted status: {0}")
    @EnumSource(value = ExchangeStatus.class, names = {"REQUESTED", "DECLINED", "COMPLETED", "CANCELLED"})
    void shouldRejectCancelFromInvalidStatuses(ExchangeStatus status) {
        UUID exchangeId = UUID.randomUUID();
        Exchange exchange = Exchange.builder()
                .id(exchangeId)
                .requester(requester)
                .receiver(receiver)
                .skillFromReceiver(skill1)
                .status(status)
                .build();
        when(exchangeRepository.findByIdForUpdate(exchangeId)).thenReturn(Optional.of(exchange));

        assertThatThrownBy(() -> exchangeService.cancel(requester.getId(), exchangeId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
    }
}
