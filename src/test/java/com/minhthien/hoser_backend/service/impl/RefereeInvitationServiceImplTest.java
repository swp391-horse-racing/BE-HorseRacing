package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.InvitationDecisionRequest;
import com.minhthien.hoser_backend.dto.request.RefereeInvitationRequest;
import com.minhthien.hoser_backend.dto.response.RefereeInvitationResponse;
import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RefereeInvitation;
import com.minhthien.hoser_backend.entity.RefereeSalaryConfig;
import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.AssignmentStatus;
import com.minhthien.hoser_backend.enums.RaceStatus;
import com.minhthien.hoser_backend.enums.TournamentStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.repository.RaceRepository;
import com.minhthien.hoser_backend.repository.RaceResultRepository;
import com.minhthien.hoser_backend.repository.RefereeInvitationRepository;
import com.minhthien.hoser_backend.repository.RefereeSalaryConfigRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.MailService;
import com.minhthien.hoser_backend.service.NotificationService;
import com.minhthien.hoser_backend.service.RefereePaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefereeInvitationServiceImplTest {
    @Mock private RefereeInvitationRepository invitationRepository;
    @Mock private RaceRepository raceRepository;
    @Mock private RaceResultRepository raceResultRepository;
    @Mock private UserRepository userRepository;
    @Mock private RefereeSalaryConfigRepository salaryConfigRepository;
    @Mock private RefereePaymentService refereePaymentService;
    @Mock private NotificationService notificationService;
    @Mock private MailService mailService;

    @InjectMocks
    private RefereeInvitationServiceImpl service;

    @Test
    void adminCanCreateMultipleInvitationsForSameRaceButNotDuplicateRecipient() {
        User admin = admin();
        User referee = referee(2L, "referee-one");
        Race race = race();
        RefereeSalaryConfig config = salaryConfig("500000");
        RefereeInvitationRequest request = request(race.getId(), referee.getId(), config.getId());
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(userRepository.findById(referee.getId())).thenReturn(Optional.of(referee));
        when(raceRepository.findByIdForUpdate(race.getId())).thenReturn(Optional.of(race));
        when(salaryConfigRepository.findById(config.getId())).thenReturn(Optional.of(config));
        when(invitationRepository.save(any())).thenAnswer(invocation -> {
            RefereeInvitation saved = invocation.getArgument(0);
            saved.setId(100L);
            return saved;
        });

        RefereeInvitationResponse response = service.createInvitation(admin.getId(), request);

        assertEquals(AssignmentStatus.PENDING, response.getStatus());
        assertEquals(referee.getId(), response.getRefereeId());
        assertEquals(new BigDecimal("500000"), response.getSalaryAmount());
        verify(invitationRepository).existsByRaceIdAndRefereeIdAndStatus(
                race.getId(), referee.getId(), AssignmentStatus.PENDING);

        when(invitationRepository.existsByRaceIdAndRefereeIdAndStatus(
                race.getId(), referee.getId(), AssignmentStatus.PENDING)).thenReturn(true);
        assertThrows(BadRequestException.class, () -> service.createInvitation(admin.getId(), request));
    }

    @Test
    void firstAcceptanceAssignsRaceUsingLatestSalaryAndCancelsOtherInvitations() {
        User admin = admin();
        User referee = referee(2L, "accepted-referee");
        User otherReferee = referee(3L, "other-referee");
        Race race = race();
        RefereeSalaryConfig originalConfig = salaryConfig("500000");
        RefereeSalaryConfig latestConfig = salaryConfig("600000");
        RefereeInvitation accepted = invitation(100L, admin, referee, race, originalConfig);
        RefereeInvitation other = invitation(101L, admin, otherReferee, race, originalConfig);
        InvitationDecisionRequest decision = new InvitationDecisionRequest();
        decision.setNote("Available");

        when(invitationRepository.findDetailedById(accepted.getId())).thenReturn(Optional.of(accepted));
        when(invitationRepository.findByIdForUpdate(accepted.getId())).thenReturn(Optional.of(accepted));
        when(raceRepository.findByIdForUpdate(race.getId())).thenReturn(Optional.of(race));
        when(userRepository.findByIdForUpdate(referee.getId())).thenReturn(Optional.of(referee));
        when(salaryConfigRepository.findById(originalConfig.getId())).thenReturn(Optional.of(latestConfig));
        when(invitationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(invitationRepository.findByRaceIdAndStatusAndIdNotOrderByCreatedAtDesc(
                race.getId(), AssignmentStatus.PENDING, accepted.getId())).thenReturn(List.of(other));
        when(invitationRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        RefereeInvitationResponse response = service.acceptInvitation(referee.getId(), accepted.getId(), decision);

        assertSame(referee, race.getReferee());
        assertEquals(AssignmentStatus.ACCEPTED, accepted.getStatus());
        assertEquals("Available", accepted.getResponseNote());
        assertEquals(AssignmentStatus.CANCELLED, other.getStatus());
        assertNotNull(other.getCancelledAt());
        assertEquals(new BigDecimal("600000"), response.getSalaryAmount());
        verify(refereePaymentService).reserveForAssignment(
                admin.getId(), race, referee, latestConfig.getId());
        verify(raceRepository).findByIdForUpdate(race.getId());
        verify(userRepository).findByIdForUpdate(referee.getId());
    }

    @Test
    void failedSalaryReservationLeavesInvitationPendingAndRaceUnassigned() {
        User admin = admin();
        User referee = referee(2L, "referee");
        Race race = race();
        RefereeSalaryConfig config = salaryConfig("600000");
        RefereeInvitation invitation = invitation(100L, admin, referee, race, config);
        when(invitationRepository.findDetailedById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(invitationRepository.findByIdForUpdate(invitation.getId())).thenReturn(Optional.of(invitation));
        when(raceRepository.findByIdForUpdate(race.getId())).thenReturn(Optional.of(race));
        when(userRepository.findByIdForUpdate(referee.getId())).thenReturn(Optional.of(referee));
        when(salaryConfigRepository.findById(config.getId())).thenReturn(Optional.of(config));
        doThrow(new BadRequestException("Insufficient admin balance"))
                .when(refereePaymentService)
                .reserveForAssignment(admin.getId(), race, referee, config.getId());

        assertThrows(BadRequestException.class,
                () -> service.acceptInvitation(referee.getId(), invitation.getId(), null));

        assertEquals(AssignmentStatus.PENDING, invitation.getStatus());
        assertNull(race.getReferee());
        verify(raceRepository, never()).save(any());
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void refereeCanRejectOnlyOwnPendingInvitation() {
        User admin = admin();
        User referee = referee(2L, "referee");
        Race race = race();
        RefereeInvitation invitation = invitation(100L, admin, referee, race, salaryConfig("500000"));
        InvitationDecisionRequest decision = new InvitationDecisionRequest();
        decision.setNote("Schedule conflict");
        when(userRepository.findById(referee.getId())).thenReturn(Optional.of(referee));
        when(invitationRepository.findByIdForUpdate(invitation.getId())).thenReturn(Optional.of(invitation));
        when(invitationRepository.save(invitation)).thenReturn(invitation);

        RefereeInvitationResponse response = service.rejectInvitation(
                referee.getId(), invitation.getId(), decision);

        assertEquals(AssignmentStatus.REJECTED, response.getStatus());
        assertEquals("Schedule conflict", response.getResponseNote());
        assertNotNull(response.getRespondedAt());
    }

    @Test
    void raceCancellationCancelsPendingAndAcceptedRefereeInvitations() {
        User admin = admin();
        User firstReferee = referee(2L, "pending-referee");
        User secondReferee = referee(3L, "accepted-referee");
        Race race = race();
        RefereeSalaryConfig config = salaryConfig("500000");
        RefereeInvitation pending = invitation(100L, admin, firstReferee, race, config);
        RefereeInvitation accepted = invitation(101L, admin, secondReferee, race, config);
        accepted.setStatus(AssignmentStatus.ACCEPTED);
        when(invitationRepository.findByRaceIdAndStatusInOrderByCreatedAtDesc(
                race.getId(), List.of(AssignmentStatus.PENDING, AssignmentStatus.ACCEPTED)))
                .thenReturn(List.of(pending, accepted));

        List<User> affected = service.cancelActiveInvitationsForRace(
                race.getId(), "Race cancelled", "SYSTEM");

        assertEquals(List.of(firstReferee, secondReferee), affected);
        assertEquals(AssignmentStatus.CANCELLED, pending.getStatus());
        assertEquals(AssignmentStatus.CANCELLED, accepted.getStatus());
        assertEquals("Race cancelled", accepted.getResponseNote());
        assertNotNull(accepted.getCancelledAt());
        verify(invitationRepository).saveAll(List.of(pending, accepted));
    }

    private RefereeInvitationRequest request(Long raceId, Long refereeId, Long salaryConfigId) {
        RefereeInvitationRequest request = new RefereeInvitationRequest();
        request.setRaceId(raceId);
        request.setRefereeId(refereeId);
        request.setSalaryConfigId(salaryConfigId);
        request.setMessage("Please referee this race");
        return request;
    }

    private RefereeInvitation invitation(Long id, User admin, User referee, Race race,
                                          RefereeSalaryConfig config) {
        return RefereeInvitation.builder()
                .id(id)
                .admin(admin)
                .referee(referee)
                .race(race)
                .salaryConfig(config)
                .status(AssignmentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private User admin() {
        return User.builder().id(1L).username("admin").role(UserRole.ADMIN).active(true).build();
    }

    private User referee(Long id, String username) {
        return User.builder().id(id).username(username).role(UserRole.REFEREE).active(true).build();
    }

    private Race race() {
        Tournament tournament = Tournament.builder()
                .id(20L)
                .name("Tournament")
                .status(TournamentStatus.PUBLISHED)
                .build();
        return Race.builder()
                .id(10L)
                .name("Race 1")
                .tournament(tournament)
                .status(RaceStatus.PUBLISHED)
                .scheduledStartAt(LocalDateTime.now().plusDays(2))
                .scheduledEndAt(LocalDateTime.now().plusDays(2).plusHours(1))
                .build();
    }

    private RefereeSalaryConfig salaryConfig(String amount) {
        return RefereeSalaryConfig.builder()
                .id(40L)
                .name("Standard")
                .raceType("STANDARD")
                .amount(new BigDecimal(amount))
                .active(true)
                .build();
    }
}
