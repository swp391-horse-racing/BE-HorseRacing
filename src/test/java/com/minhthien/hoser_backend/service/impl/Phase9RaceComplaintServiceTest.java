package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.RaceComplaintRequest;
import com.minhthien.hoser_backend.dto.request.RaceComplaintResolveRequest;
import com.minhthien.hoser_backend.entity.Horse;
import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RaceComplaint;
import com.minhthien.hoser_backend.entity.RaceParticipant;
import com.minhthien.hoser_backend.entity.RaceRegistration;
import com.minhthien.hoser_backend.entity.RaceResult;
import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.RaceComplaintStatus;
import com.minhthien.hoser_backend.enums.RaceParticipantStatus;
import com.minhthien.hoser_backend.enums.RaceStatus;
import com.minhthien.hoser_backend.enums.TournamentStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.enums.WalletTransactionType;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.exception.UnauthorizedException;
import com.minhthien.hoser_backend.repository.JockeyChallengeResultRepository;
import com.minhthien.hoser_backend.repository.JockeyInvitationRepository;
import com.minhthien.hoser_backend.repository.RaceComplaintRepository;
import com.minhthien.hoser_backend.repository.RaceParticipantRepository;
import com.minhthien.hoser_backend.repository.RaceRegistrationRepository;
import com.minhthien.hoser_backend.repository.RaceRepository;
import com.minhthien.hoser_backend.repository.RaceResultRepository;
import com.minhthien.hoser_backend.repository.TournamentRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.BettingService;
import com.minhthien.hoser_backend.service.FinanceSettingsService;
import com.minhthien.hoser_backend.service.MailService;
import com.minhthien.hoser_backend.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Phase9RaceComplaintServiceTest {
    @Mock
    private RaceRepository raceRepository;
    @Mock
    private RaceRegistrationRepository raceRegistrationRepository;
    @Mock
    private RaceParticipantRepository raceParticipantRepository;
    @Mock
    private RaceResultRepository raceResultRepository;
    @Mock
    private RaceComplaintRepository raceComplaintRepository;
    @Mock
    private JockeyChallengeResultRepository jockeyChallengeResultRepository;
    @Mock
    private JockeyInvitationRepository jockeyInvitationRepository;
    @Mock
    private TournamentRepository tournamentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WalletService walletService;
    @Mock
    private TournamentServiceImpl tournamentService;
    @Mock
    private FinanceSettingsService financeSettingsService;
    @Mock
    private MailService mailService;
    @Mock
    private BettingService bettingService;

    @Test
    void ownerInSameRaceCreatesComplaintWithin24HoursAndEmailHidesComplainant() {
        RaceDayServiceImpl service = service();
        User complainant = user(1L, "complainant", UserRole.OWNER);
        User accused = user(2L, "accused", UserRole.OWNER);
        Race race = confirmedRace(LocalDateTime.now().minusHours(2));
        RaceParticipant complainantParticipant = participant(101L, race, complainant);
        RaceParticipant accusedParticipant = participant(102L, race, accused);
        RaceComplaintRequest request = complaintRequest(102L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(complainant));
        when(raceRepository.findById(10L)).thenReturn(Optional.of(race));
        when(raceParticipantRepository.findById(102L)).thenReturn(Optional.of(accusedParticipant));
        when(raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(10L))
                .thenReturn(List.of(complainantParticipant, accusedParticipant));
        when(raceComplaintRepository.save(any(RaceComplaint.class))).thenAnswer(invocation -> {
            RaceComplaint complaint = invocation.getArgument(0);
            complaint.setId(301L);
            return complaint;
        });

        var response = service.createRaceComplaint(1L, 10L, request);

        assertThat(response.getId()).isEqualTo(301L);
        assertThat(response.getStatus()).isEqualTo(RaceComplaintStatus.PENDING);
        assertThat(response.getComplainantOwnerId()).isNull();
        assertThat(response.getAccusedOwnerId()).isEqualTo(2L);
        verify(mailService).sendRaceComplaintCreated(any(RaceComplaint.class));
    }

    @Test
    void ownerOutsideRaceCannotCreateComplaint() {
        RaceDayServiceImpl service = service();
        User outsider = user(5L, "outsider", UserRole.OWNER);
        User accused = user(2L, "accused", UserRole.OWNER);
        Race race = confirmedRace(LocalDateTime.now().minusHours(2));
        RaceParticipant accusedParticipant = participant(102L, race, accused);

        when(userRepository.findById(5L)).thenReturn(Optional.of(outsider));
        when(raceRepository.findById(10L)).thenReturn(Optional.of(race));
        when(raceParticipantRepository.findById(102L)).thenReturn(Optional.of(accusedParticipant));
        when(raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(10L))
                .thenReturn(List.of(accusedParticipant));

        assertThatThrownBy(() -> service.createRaceComplaint(5L, 10L, complaintRequest(102L)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Only owners in this race can create complaints");
    }

    @Test
    void complaintAfter24HoursIsRejected() {
        RaceDayServiceImpl service = service();
        User complainant = user(1L, "complainant", UserRole.OWNER);
        Race race = confirmedRace(LocalDateTime.now().minusHours(25));

        when(userRepository.findById(1L)).thenReturn(Optional.of(complainant));
        when(raceRepository.findById(10L)).thenReturn(Optional.of(race));

        assertThatThrownBy(() -> service.createRaceComplaint(1L, 10L, complaintRequest(102L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Race complaint window has expired");
    }

    @Test
    void adminRejectsComplaintWithoutWalletOrBanChanges() {
        RaceDayServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        RaceComplaint complaint = pendingComplaint();
        RaceComplaintResolveRequest request = new RaceComplaintResolveRequest();
        request.setStatus(RaceComplaintStatus.REJECTED);
        request.setAdminNote("Not valid");

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(raceComplaintRepository.findById(301L)).thenReturn(Optional.of(complaint));
        when(raceComplaintRepository.save(complaint)).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.resolveRaceComplaint(9L, 301L, request);

        assertThat(response.getStatus()).isEqualTo(RaceComplaintStatus.REJECTED);
        verify(walletService, never()).debitAllowNegative(any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(complaint.getAccusedOwner().getOwnerBanUntil()).isNull();
    }

    @Test
    void adminApprovesComplaintSetsBanAndDebitsOwnerPrizeAndFineAllowingNegative() {
        RaceDayServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        RaceComplaint complaint = pendingComplaint();
        RaceComplaintResolveRequest request = new RaceComplaintResolveRequest();
        request.setStatus(RaceComplaintStatus.APPROVED);
        request.setBanUntil(LocalDateTime.of(2026, 7, 1, 0, 0));
        request.setFineAmount(new BigDecimal("30000.00"));
        request.setAdminNote("Violation confirmed");
        RaceResult result = RaceResult.builder()
                .id(501L)
                .participant(complaint.getAccusedParticipant())
                .owner(complaint.getAccusedOwner())
                .ownerPrizeAmount(new BigDecimal("80000.00"))
                .jockeyPrizeAmount(new BigDecimal("20000.00"))
                .prizeAmount(new BigDecimal("100000.00"))
                .build();

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(raceComplaintRepository.findById(301L)).thenReturn(Optional.of(complaint));
        when(raceResultRepository.findByParticipantId(102L)).thenReturn(Optional.of(result));
        when(userRepository.save(complaint.getAccusedOwner())).thenAnswer(invocation -> invocation.getArgument(0));
        when(raceComplaintRepository.save(complaint)).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.resolveRaceComplaint(9L, 301L, request);

        assertThat(response.getStatus()).isEqualTo(RaceComplaintStatus.APPROVED);
        assertThat(response.getOwnerPrizeReturnAmount()).isEqualByComparingTo("80000.00");
        assertThat(response.getFineAmount()).isEqualByComparingTo("30000.00");
        assertThat(complaint.getAccusedOwner().getOwnerBanUntil()).isEqualTo(request.getBanUntil());
        verify(walletService).debitAllowNegative(eq(2L), eq(new BigDecimal("80000.00")),
                eq(WalletTransactionType.ADJUSTMENT), eq("RACE_COMPLAINT"), eq("301"),
                eq("race-complaint:301:owner-prize-return"), any(), eq("Race complaint owner prize return"));
        verify(walletService).debitAllowNegative(eq(2L), eq(new BigDecimal("30000.00")),
                eq(WalletTransactionType.ADJUSTMENT), eq("RACE_COMPLAINT"), eq("301"),
                eq("race-complaint:301:fine"), any(), eq("Race complaint fine"));
    }

    private RaceDayServiceImpl service() {
        return new RaceDayServiceImpl(raceRepository, raceRegistrationRepository, raceParticipantRepository,
                raceResultRepository, raceComplaintRepository, jockeyChallengeResultRepository,
                jockeyInvitationRepository, tournamentRepository, userRepository, walletService, tournamentService,
                financeSettingsService, mailService, bettingService);
    }

    private RaceComplaint pendingComplaint() {
        User complainant = user(1L, "complainant", UserRole.OWNER);
        User accused = user(2L, "accused", UserRole.OWNER);
        Race race = confirmedRace(LocalDateTime.now().minusHours(2));
        RaceParticipant participant = participant(102L, race, accused);
        return RaceComplaint.builder()
                .id(301L)
                .race(race)
                .complainantOwner(complainant)
                .accusedOwner(accused)
                .accusedParticipant(participant)
                .reason("Lane violation")
                .status(RaceComplaintStatus.PENDING)
                .build();
    }

    private Race confirmedRace(LocalDateTime finalizedAt) {
        return Race.builder()
                .id(10L)
                .tournament(Tournament.builder()
                        .id(20L)
                        .name("Summer Race Day")
                        .location("Ho Chi Minh City")
                        .status(TournamentStatus.SCHEDULED)
                        .build())
                .name("Sprint")
                .distance("1000m")
                .scheduledStartAt(LocalDateTime.of(2026, 6, 16, 9, 0))
                .scheduledEndAt(LocalDateTime.of(2026, 6, 16, 9, 30))
                .minParticipants(1)
                .maxParticipants(8)
                .status(RaceStatus.RESULT_CONFIRMED)
                .resultFinalizedAt(finalizedAt)
                .build();
    }

    private RaceParticipant participant(Long id, Race race, User owner) {
        Horse horse = Horse.builder().id(id + 1000).name("Horse " + id).owner(owner).build();
        RaceRegistration registration = RaceRegistration.builder()
                .id(id + 2000)
                .race(race)
                .owner(owner)
                .horse(horse)
                .jockey(user(id + 3000, "jockey-" + id, UserRole.JOCKEY))
                .build();
        return RaceParticipant.builder()
                .id(id)
                .race(race)
                .registration(registration)
                .owner(owner)
                .horse(horse)
                .jockey(registration.getJockey())
                .gateNumber(id.intValue())
                .status(RaceParticipantStatus.FINISHED)
                .build();
    }

    private RaceComplaintRequest complaintRequest(Long accusedParticipantId) {
        RaceComplaintRequest request = new RaceComplaintRequest();
        request.setAccusedParticipantId(accusedParticipantId);
        request.setReason("Lane violation");
        request.setEvidenceUrl("https://example.com/evidence");
        return request;
    }

    private User user(Long id, String username, UserRole role) {
        return User.builder()
                .id(id)
                .username(username)
                .email(username + "@example.com")
                .role(role)
                .active(true)
                .build();
    }
}
