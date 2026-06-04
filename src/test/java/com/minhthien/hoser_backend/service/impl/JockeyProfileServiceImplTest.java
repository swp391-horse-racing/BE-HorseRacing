package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.response.JockeyProfileResponse;
import com.minhthien.hoser_backend.entity.Horse;
import com.minhthien.hoser_backend.entity.JockeyProfile;
import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RaceResult;
import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.JockeyStatus;
import com.minhthien.hoser_backend.enums.RaceParticipantStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.repository.JockeyProfileRepository;
import com.minhthien.hoser_backend.repository.RaceResultRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.CloudinaryUploadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JockeyProfileServiceImplTest {
    @Mock
    private JockeyProfileRepository jockeyProfileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CloudinaryUploadService cloudinaryUploadService;
    @Mock
    private RaceResultRepository raceResultRepository;

    @InjectMocks
    private JockeyProfileServiceImpl service;

    @Test
    void jockeyDetailIncludesEmptyPerformanceWhenJockeyHasNoResults() {
        JockeyProfile profile = profile(20L);
        when(jockeyProfileRepository.findByUserId(20L)).thenReturn(Optional.of(profile));
        when(raceResultRepository.findByJockeyIdOrderByRaceScheduledStartAtDesc(20L)).thenReturn(List.of());

        JockeyProfileResponse response = service.getApprovedJockeyProfile(20L);

        assertEquals(0, response.getPerformance().getTotalRaces());
        assertEquals(0, response.getPerformance().getWins());
        assertEquals(new BigDecimal("0.00"), response.getPerformance().getWinRate());
        assertTrue(response.getPerformance().getRankCounts().isEmpty());
        assertTrue(response.getRaceHistory().isEmpty());
    }

    @Test
    void jockeyDetailIncludesPerformanceAndRaceHistoryFromResults() {
        JockeyProfile profile = profile(20L);
        List<RaceResult> results = List.of(
                result(1L, 1, LocalDateTime.of(2026, 7, 15, 10, 0)),
                result(2L, 1, LocalDateTime.of(2026, 7, 14, 10, 0)),
                result(3L, 2, LocalDateTime.of(2026, 7, 13, 10, 0)),
                result(4L, 5, LocalDateTime.of(2026, 7, 12, 10, 0)),
                result(5L, null, LocalDateTime.of(2026, 7, 11, 10, 0))
        );
        when(jockeyProfileRepository.findByUserId(20L)).thenReturn(Optional.of(profile));
        when(raceResultRepository.findByJockeyIdOrderByRaceScheduledStartAtDesc(20L)).thenReturn(results);

        JockeyProfileResponse response = service.getApprovedJockeyProfile(20L);

        assertEquals(5, response.getPerformance().getTotalRaces());
        assertEquals(2, response.getPerformance().getWins());
        assertEquals(new BigDecimal("40.00"), response.getPerformance().getWinRate());
        assertEquals(2L, response.getPerformance().getRankCounts().get("1"));
        assertEquals(1L, response.getPerformance().getRankCounts().get("2"));
        assertEquals(1L, response.getPerformance().getRankCounts().get("5"));
        assertEquals(3, response.getPerformance().getRankCounts().size());
        assertEquals(5, response.getRaceHistory().size());
        assertEquals(1L, response.getRaceHistory().get(0).getTournamentId());
        assertEquals("Summer Cup", response.getRaceHistory().get(0).getTournamentName());
        assertEquals(101L, response.getRaceHistory().get(0).getRaceId());
        assertEquals("Race 1", response.getRaceHistory().get(0).getRaceName());
        assertEquals(10L, response.getRaceHistory().get(0).getHorseId());
        assertEquals("Lightning", response.getRaceHistory().get(0).getHorseName());
        assertEquals(1, response.getRaceHistory().get(0).getRank());
        assertEquals(RaceParticipantStatus.FINISHED, response.getRaceHistory().get(0).getStatus());
        assertEquals(61000L, response.getRaceHistory().get(0).getFinishTimeMillis());
        assertEquals(LocalDateTime.of(2026, 7, 15, 12, 0), response.getRaceHistory().get(0).getFinalizedAt());
    }

    private JockeyProfile profile(Long userId) {
        return JockeyProfile.builder()
                .id(30L)
                .user(User.builder()
                        .id(userId)
                        .username("jockey")
                        .fullName("Jockey One")
                        .role(UserRole.JOCKEY)
                        .build())
                .licenseNumber("JCK-001")
                .status(JockeyStatus.APPROVED)
                .build();
    }

    private RaceResult result(Long id, Integer rank, LocalDateTime scheduledStartAt) {
        Tournament tournament = Tournament.builder()
                .id(1L)
                .name("Summer Cup")
                .build();
        Race race = Race.builder()
                .id(100L + id)
                .tournament(tournament)
                .name("Race " + id)
                .scheduledStartAt(scheduledStartAt)
                .build();
        Horse horse = Horse.builder()
                .id(10L)
                .name("Lightning")
                .build();
        return RaceResult.builder()
                .id(id)
                .race(race)
                .horse(horse)
                .rank(rank)
                .status(RaceParticipantStatus.FINISHED)
                .finishTimeMillis(60000L + id * 1000)
                .finalizedAt(LocalDateTime.of(2026, 7, 15, 12, 0))
                .build();
    }
}
