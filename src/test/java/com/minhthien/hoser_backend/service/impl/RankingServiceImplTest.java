package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.response.RankingResponse;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.repository.RaceResultRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingServiceImplTest {
    @Mock
    private RaceResultRepository raceResultRepository;

    @InjectMocks
    private RankingServiceImpl service;

    @Test
    void rankingsUseCompetitionRankForEqualHorseAndJockeyStatistics() {
        when(raceResultRepository.findHorseRankingStatistics(any(Pageable.class)))
                .thenReturn(List.<Object[]>of(
                        new Object[]{1L, "Thunder Bolt", 11L, "Owner A", 4L, 5L, 8L, new BigDecimal("900")},
                        new Object[]{2L, "Black Pearl", 12L, "Owner B", 4L, 5L, 8L, new BigDecimal("900")},
                        new Object[]{3L, "Wind Runner", 13L, "Owner C", 3L, 6L, 9L, new BigDecimal("1000")}));
        when(raceResultRepository.findJockeyRankingStatistics(any(Pageable.class)))
                .thenReturn(List.<Object[]>of(
                        new Object[]{21L, "jockey-a", "Jockey A", 5L, 7L, 10L, new BigDecimal("500")},
                        new Object[]{22L, "jockey-b", "Jockey B", 5L, 7L, 10L, new BigDecimal("500")},
                        new Object[]{23L, "jockey-c", "Jockey C", 4L, 8L, 11L, new BigDecimal("600")}));

        RankingResponse response = service.getRankings(3);

        assertEquals("WIN_COUNT", response.getMetric());
        assertNotNull(response.getGeneratedAt());
        assertEquals(List.of(1, 1, 3), response.getHorses().stream()
                .map(entry -> entry.getRank()).toList());
        assertEquals(List.of(1, 1, 3), response.getJockeys().stream()
                .map(entry -> entry.getRank()).toList());
    }

    @Test
    void rankingEntriesMapAggregateColumns() {
        when(raceResultRepository.findHorseRankingStatistics(any(Pageable.class)))
                .thenReturn(List.<Object[]>of(
                        new Object[]{7L, "Silver Arrow", 17L, "Owner Seven", 2L, 4L, 6L,
                                new BigDecimal("1234.50")}));
        when(raceResultRepository.findJockeyRankingStatistics(any(Pageable.class)))
                .thenReturn(List.<Object[]>of(
                        new Object[]{27L, "jockey-seven", "Jockey Seven", 3L, 5L, 8L,
                                new BigDecimal("456.75")}));

        RankingResponse response = service.getRankings(20);

        assertEquals(7L, response.getHorses().get(0).getHorseId());
        assertEquals("Silver Arrow", response.getHorses().get(0).getHorseName());
        assertEquals(17L, response.getHorses().get(0).getOwnerId());
        assertEquals("Owner Seven", response.getHorses().get(0).getOwnerName());
        assertEquals(2L, response.getHorses().get(0).getWinCount());
        assertEquals(4L, response.getHorses().get(0).getPodiumCount());
        assertEquals(6L, response.getHorses().get(0).getRaceCount());
        assertEquals(new BigDecimal("1234.50"), response.getHorses().get(0).getTotalPrizeAmount());

        assertEquals(27L, response.getJockeys().get(0).getJockeyId());
        assertEquals("jockey-seven", response.getJockeys().get(0).getJockeyUsername());
        assertEquals("Jockey Seven", response.getJockeys().get(0).getJockeyFullName());
        assertEquals(3L, response.getJockeys().get(0).getWinCount());
        assertEquals(5L, response.getJockeys().get(0).getPodiumCount());
        assertEquals(8L, response.getJockeys().get(0).getRaceCount());
        assertEquals(new BigDecimal("456.75"), response.getJockeys().get(0).getTotalPrizeAmount());
    }

    @Test
    void invalidLimitsAreRejected() {
        assertThrows(BadRequestException.class, () -> service.getRankings(0));
        assertThrows(BadRequestException.class, () -> service.getRankings(101));
    }

    @Test
    void repositoryQueriesUseRequestedLimit() {
        when(raceResultRepository.findHorseRankingStatistics(any(Pageable.class))).thenReturn(List.of());
        when(raceResultRepository.findJockeyRankingStatistics(any(Pageable.class))).thenReturn(List.of());

        service.getRankings(42);

        verify(raceResultRepository).findHorseRankingStatistics(Pageable.ofSize(42));
        verify(raceResultRepository).findJockeyRankingStatistics(Pageable.ofSize(42));
    }
}
