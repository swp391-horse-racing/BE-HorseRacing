package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.response.TournamentResponse;
import com.minhthien.hoser_backend.entity.Horse;
import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RaceComplaint;
import com.minhthien.hoser_backend.entity.RaceParticipant;
import com.minhthien.hoser_backend.entity.RaceRegistration;
import com.minhthien.hoser_backend.entity.RaceResult;
import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.entity.TournamentLeaderboardSnapshot;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.RaceComplaintStatus;
import com.minhthien.hoser_backend.enums.RaceParticipantStatus;
import com.minhthien.hoser_backend.enums.RacePayoutStatus;
import com.minhthien.hoser_backend.enums.RaceRegistrationStatus;
import com.minhthien.hoser_backend.enums.RaceStatus;
import com.minhthien.hoser_backend.enums.TournamentStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.repository.RaceComplaintRepository;
import com.minhthien.hoser_backend.repository.RaceParticipantRepository;
import com.minhthien.hoser_backend.repository.RaceRegistrationRepository;
import com.minhthien.hoser_backend.repository.RaceRepository;
import com.minhthien.hoser_backend.repository.RaceResultRepository;
import com.minhthien.hoser_backend.repository.TournamentLeaderboardSnapshotRepository;
import com.minhthien.hoser_backend.repository.TournamentRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.RaceDayService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Phase10TournamentFinalizationServiceTest {
    @Mock
    private TournamentRepository tournamentRepository;
    @Mock
    private RaceRepository raceRepository;
    @Mock
    private RaceResultRepository raceResultRepository;
    @Mock
    private RaceRegistrationRepository raceRegistrationRepository;
    @Mock
    private RaceParticipantRepository raceParticipantRepository;
    @Mock
    private RaceComplaintRepository raceComplaintRepository;
    @Mock
    private TournamentLeaderboardSnapshotRepository leaderboardSnapshotRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RaceDayService raceDayService;
    @Mock
    private TournamentServiceImpl tournamentService;

    @Test
    void finalizeCompletesTournamentWithSnapshotPendingComplaintAndUnpaidDebtSplit() {
        TournamentFinalizationServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        Tournament tournament = tournament(TournamentStatus.SCHEDULED);
        Race race = race(tournament, RaceStatus.RESULT_CONFIRMED);
        RaceResult result = result(race, RacePayoutStatus.UNPAID);
        AtomicReference<List<TournamentLeaderboardSnapshot>> snapshots = new AtomicReference<>(List.of());

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(tournamentRepository.findById(20L)).thenReturn(Optional.of(tournament));
        when(raceRepository.findByTournamentIdOrderByScheduledStartAtAsc(20L)).thenReturn(List.of(race));
        when(raceResultRepository.findByRaceTournamentId(20L)).thenReturn(List.of(result));
        when(raceComplaintRepository.countByRaceTournamentIdAndStatus(20L, RaceComplaintStatus.PENDING))
                .thenReturn(1L);
        when(tournamentRepository.save(tournament)).thenAnswer(invocation -> invocation.getArgument(0));
        when(leaderboardSnapshotRepository.existsByTournamentId(20L)).thenReturn(false);
        when(leaderboardSnapshotRepository.saveAll(any())).thenAnswer(invocation -> {
            List<TournamentLeaderboardSnapshot> saved = new ArrayList<>(invocation.getArgument(0));
            saved.forEach(snapshot -> snapshot.setId(800L));
            snapshots.set(saved);
            return saved;
        });
        when(leaderboardSnapshotRepository.findByTournamentIdOrderByRaceScheduledStartAtAscRaceRankAscIdAsc(20L))
                .thenAnswer(invocation -> snapshots.get());
        when(tournamentService.mapToResponse(tournament)).thenReturn(TournamentResponse.builder()
                .id(20L)
                .status(TournamentStatus.COMPLETED)
                .build());
        when(raceRegistrationRepository.findByRaceTournamentIdOrderByCreatedAtDesc(20L))
                .thenReturn(List.of(registration(race, result.getOwner(), result.getHorse(), result.getJockey())));
        when(raceParticipantRepository.findByRaceTournamentId(20L)).thenReturn(List.of(result.getParticipant()));
        when(raceComplaintRepository.findByRaceTournamentId(20L)).thenReturn(List.of(complaint(race)));

        var response = service.finalizeTournament(9L, 20L);

        assertThat(tournament.getStatus()).isEqualTo(TournamentStatus.COMPLETED);
        assertThat(tournament.getFinalizedBy()).isEqualTo(9L);
        assertThat(tournament.getPendingComplaintCountAtFinalize()).isEqualTo(1);
        assertThat(response.getLeaderboard().getEntries()).hasSize(1);
        assertThat(response.getLeaderboard().getEntries().get(0).getHorseName()).isEqualTo("Thunder");
        assertThat(response.getPayouts()).hasSize(1);
        assertThat(response.getPayouts().get(0).getUnpaidOwnerAmount()).isEqualByComparingTo("800000.00");
        assertThat(response.getPayouts().get(0).getUnpaidJockeyAmount()).isEqualByComparingTo("200000.00");
    }

    @Test
    void finalizeRejectsTournamentWithUnfinishedRace() {
        TournamentFinalizationServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        Tournament tournament = tournament(TournamentStatus.SCHEDULED);
        Race race = race(tournament, RaceStatus.SCHEDULED);

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(tournamentRepository.findById(20L)).thenReturn(Optional.of(tournament));
        when(raceRepository.findByTournamentIdOrderByScheduledStartAtAsc(20L)).thenReturn(List.of(race));

        assertThatThrownBy(() -> service.finalizeTournament(9L, 20L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("All races must be result-confirmed or cancelled before finalizing");
        verify(tournamentRepository, never()).save(any());
        verify(leaderboardSnapshotRepository, never()).saveAll(any());
    }

    @Test
    void finalizeCompletedTournamentWithSnapshotsIsIdempotent() {
        TournamentFinalizationServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        Tournament tournament = tournament(TournamentStatus.COMPLETED);
        tournament.setFinalizedBy(9L);
        tournament.setFinalizedAt(LocalDateTime.of(2026, 6, 1, 12, 0));
        Race race = race(tournament, RaceStatus.RESULT_CONFIRMED);
        RaceResult result = result(race, RacePayoutStatus.PAID);
        TournamentLeaderboardSnapshot snapshot = snapshot(tournament, result, "Snapshot Horse");

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(tournamentRepository.findById(20L)).thenReturn(Optional.of(tournament));
        when(leaderboardSnapshotRepository.existsByTournamentId(20L)).thenReturn(true);
        when(leaderboardSnapshotRepository.findByTournamentIdOrderByRaceScheduledStartAtAscRaceRankAscIdAsc(20L))
                .thenReturn(List.of(snapshot));
        when(raceResultRepository.findByRaceTournamentId(20L)).thenReturn(List.of(result));
        when(raceRepository.findByTournamentIdOrderByScheduledStartAtAsc(20L)).thenReturn(List.of(race));
        when(raceRegistrationRepository.findByRaceTournamentIdOrderByCreatedAtDesc(20L))
                .thenReturn(List.of(registration(race, result.getOwner(), result.getHorse(), result.getJockey())));
        when(raceParticipantRepository.findByRaceTournamentId(20L)).thenReturn(List.of(result.getParticipant()));
        when(raceComplaintRepository.findByRaceTournamentId(20L)).thenReturn(List.of());
        when(tournamentService.mapToResponse(tournament)).thenReturn(TournamentResponse.builder()
                .id(20L)
                .status(TournamentStatus.COMPLETED)
                .build());

        var response = service.finalizeTournament(9L, 20L);

        assertThat(response.getLeaderboard().getEntries()).hasSize(1);
        verify(tournamentRepository, never()).save(any());
        verify(leaderboardSnapshotRepository, never()).saveAll(any());
    }

    @Test
    void publicLeaderboardReadsSnapshotNames() {
        TournamentFinalizationServiceImpl service = service();
        Tournament tournament = tournament(TournamentStatus.COMPLETED);
        Race race = race(tournament, RaceStatus.RESULT_CONFIRMED);
        RaceResult result = result(race, RacePayoutStatus.PAID);
        result.getHorse().setName("Changed Horse");
        TournamentLeaderboardSnapshot snapshot = snapshot(tournament, result, "Original Horse");

        when(tournamentRepository.findById(20L)).thenReturn(Optional.of(tournament));
        when(leaderboardSnapshotRepository.findByTournamentIdOrderByRaceScheduledStartAtAscRaceRankAscIdAsc(20L))
                .thenReturn(List.of(snapshot));

        var response = service.getLeaderboard(20L);

        assertThat(response.getEntries()).hasSize(1);
        assertThat(response.getEntries().get(0).getHorseName()).isEqualTo("Original Horse");
    }

    private TournamentFinalizationServiceImpl service() {
        return new TournamentFinalizationServiceImpl(tournamentRepository, raceRepository, raceResultRepository,
                raceRegistrationRepository, raceParticipantRepository, raceComplaintRepository,
                leaderboardSnapshotRepository, userRepository, raceDayService, tournamentService);
    }

    private Tournament tournament(TournamentStatus status) {
        return Tournament.builder()
                .id(20L)
                .name("Summer Championship")
                .location("Ho Chi Minh City")
                .status(status)
                .registrationOpenAt(LocalDateTime.of(2026, 5, 1, 8, 0))
                .registrationCloseAt(LocalDateTime.of(2026, 5, 10, 8, 0))
                .startAt(LocalDateTime.of(2026, 6, 1, 8, 0))
                .endAt(LocalDateTime.of(2026, 6, 1, 18, 0))
                .minTeams(1)
                .maxTeams(8)
                .jockeyChallengeEnabled(false)
                .build();
    }

    private Race race(Tournament tournament, RaceStatus status) {
        return Race.builder()
                .id(10L)
                .tournament(tournament)
                .name("Final Heat")
                .distance("1000m")
                .scheduledStartAt(LocalDateTime.of(2026, 6, 1, 9, 0))
                .scheduledEndAt(LocalDateTime.of(2026, 6, 1, 9, 30))
                .minParticipants(1)
                .maxParticipants(8)
                .referee(user(8L, "referee", UserRole.REFEREE))
                .status(status)
                .build();
    }

    private RaceResult result(Race race, RacePayoutStatus payoutStatus) {
        User owner = user(1L, "owner", UserRole.OWNER);
        User jockey = user(2L, "jockey", UserRole.JOCKEY);
        Horse horse = Horse.builder().id(101L).name("Thunder").owner(owner).build();
        RaceRegistration registration = registration(race, owner, horse, jockey);
        RaceParticipant participant = RaceParticipant.builder()
                .id(201L)
                .race(race)
                .registration(registration)
                .owner(owner)
                .horse(horse)
                .jockey(jockey)
                .gateNumber(1)
                .status(RaceParticipantStatus.FINISHED)
                .build();
        return RaceResult.builder()
                .id(301L)
                .race(race)
                .participant(participant)
                .owner(owner)
                .horse(horse)
                .jockey(jockey)
                .rank(1)
                .finishTimeMillis(60000L)
                .status(RaceParticipantStatus.FINISHED)
                .prizeAmount(new BigDecimal("1000000.00"))
                .ownerPrizeAmount(new BigDecimal("800000.00"))
                .jockeyPrizeAmount(new BigDecimal("200000.00"))
                .jockeyPrizePercent(new BigDecimal("20.00"))
                .payoutStatus(payoutStatus)
                .finalizedBy(8L)
                .finalizedAt(LocalDateTime.of(2026, 6, 1, 10, 0))
                .build();
    }

    private RaceRegistration registration(Race race, User owner, Horse horse, User jockey) {
        return RaceRegistration.builder()
                .id(401L)
                .race(race)
                .owner(owner)
                .horse(horse)
                .jockey(jockey)
                .status(RaceRegistrationStatus.APPROVED)
                .build();
    }

    private RaceComplaint complaint(Race race) {
        return RaceComplaint.builder()
                .id(501L)
                .race(race)
                .complainantOwner(user(3L, "complainant", UserRole.OWNER))
                .accusedOwner(user(1L, "owner", UserRole.OWNER))
                .accusedParticipant(RaceParticipant.builder()
                        .id(201L)
                        .race(race)
                        .owner(user(1L, "owner", UserRole.OWNER))
                        .horse(Horse.builder().id(101L).name("Thunder").build())
                        .jockey(user(2L, "jockey", UserRole.JOCKEY))
                        .gateNumber(1)
                        .build())
                .status(RaceComplaintStatus.PENDING)
                .reason("Lane violation")
                .build();
    }

    private TournamentLeaderboardSnapshot snapshot(Tournament tournament, RaceResult result, String horseName) {
        return TournamentLeaderboardSnapshot.builder()
                .id(800L)
                .tournament(tournament)
                .raceId(result.getRace().getId())
                .raceName(result.getRace().getName())
                .raceScheduledStartAt(result.getRace().getScheduledStartAt())
                .raceScheduledEndAt(result.getRace().getScheduledEndAt())
                .raceResultId(result.getId())
                .participantId(result.getParticipant().getId())
                .raceRank(result.getRank())
                .finishTimeMillis(result.getFinishTimeMillis())
                .resultStatus(result.getStatus())
                .horseId(result.getHorse().getId())
                .horseName(horseName)
                .ownerId(result.getOwner().getId())
                .ownerUsername(result.getOwner().getUsername())
                .jockeyId(result.getJockey().getId())
                .jockeyUsername(result.getJockey().getUsername())
                .prizeAmount(result.getPrizeAmount())
                .ownerPrizeAmount(result.getOwnerPrizeAmount())
                .jockeyPrizeAmount(result.getJockeyPrizeAmount())
                .jockeyPrizePercent(result.getJockeyPrizePercent())
                .payoutStatus(result.getPayoutStatus())
                .resultFinalizedBy(result.getFinalizedBy())
                .resultFinalizedAt(result.getFinalizedAt())
                .tournamentFinalizedBy(9L)
                .tournamentFinalizedAt(LocalDateTime.of(2026, 6, 1, 12, 0))
                .build();
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
