package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.response.HorseRankingEntryResponse;
import com.minhthien.hoser_backend.dto.response.JockeyRankingEntryResponse;
import com.minhthien.hoser_backend.dto.response.RankingResponse;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.repository.RaceResultRepository;
import com.minhthien.hoser_backend.service.RankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RankingServiceImpl implements RankingService {
    private static final String METRIC_WIN_COUNT = "WIN_COUNT";

    private final RaceResultRepository raceResultRepository;

    @Override
    @Transactional(readOnly = true)
    public RankingResponse getRankings(int limit) {
        validateRange(limit, 1, 100, "limit");

        return RankingResponse.builder()
                .generatedAt(LocalDateTime.now())
                .metric(METRIC_WIN_COUNT)
                .horses(mapHorseRankings(raceResultRepository.findHorseRankingStatistics(PageRequest.of(0, limit))))
                .jockeys(mapJockeyRankings(raceResultRepository.findJockeyRankingStatistics(PageRequest.of(0, limit))))
                .build();
    }

    private List<HorseRankingEntryResponse> mapHorseRankings(List<Object[]> rows) {
        List<HorseRankingEntryResponse> response = new ArrayList<>();
        RankingTie previous = null;
        int rank = 0;

        for (int index = 0; index < rows.size(); index++) {
            Object[] row = rows.get(index);
            RankingTie current = tie(row[4], row[5], row[6], row[7]);
            if (previous == null || !previous.sameScoreAs(current)) {
                rank = index + 1;
            }
            response.add(HorseRankingEntryResponse.builder()
                    .rank(rank)
                    .horseId(number(row[0]).longValue())
                    .horseName((String) row[1])
                    .ownerId(number(row[2]).longValue())
                    .ownerName((String) row[3])
                    .winCount(current.winCount())
                    .podiumCount(current.podiumCount())
                    .raceCount(current.raceCount())
                    .totalPrizeAmount(current.totalPrizeAmount())
                    .build());
            previous = current;
        }

        return response;
    }

    private List<JockeyRankingEntryResponse> mapJockeyRankings(List<Object[]> rows) {
        List<JockeyRankingEntryResponse> response = new ArrayList<>();
        RankingTie previous = null;
        int rank = 0;

        for (int index = 0; index < rows.size(); index++) {
            Object[] row = rows.get(index);
            RankingTie current = tie(row[3], row[4], row[5], row[6]);
            if (previous == null || !previous.sameScoreAs(current)) {
                rank = index + 1;
            }
            response.add(JockeyRankingEntryResponse.builder()
                    .rank(rank)
                    .jockeyId(number(row[0]).longValue())
                    .jockeyUsername((String) row[1])
                    .jockeyFullName((String) row[2])
                    .winCount(current.winCount())
                    .podiumCount(current.podiumCount())
                    .raceCount(current.raceCount())
                    .totalPrizeAmount(current.totalPrizeAmount())
                    .build());
            previous = current;
        }

        return response;
    }

    private RankingTie tie(Object winCount, Object podiumCount, Object raceCount, Object totalPrizeAmount) {
        return new RankingTie(
                number(winCount).longValue(),
                number(podiumCount).longValue(),
                number(raceCount).longValue(),
                decimal(totalPrizeAmount));
    }

    private Number number(Object value) {
        return (Number) value;
    }

    private BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return BigDecimal.ZERO;
    }

    private void validateRange(int value, int min, int max, String field) {
        if (value < min || value > max) {
            throw new BadRequestException(field + " must be between " + min + " and " + max);
        }
    }

    private record RankingTie(long winCount, long podiumCount, long raceCount, BigDecimal totalPrizeAmount) {
        private boolean sameScoreAs(RankingTie other) {
            return winCount == other.winCount
                    && podiumCount == other.podiumCount
                    && raceCount == other.raceCount
                    && totalPrizeAmount.compareTo(other.totalPrizeAmount) == 0;
        }
    }
}
