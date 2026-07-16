package com.minhthien.hoser_backend.service;

import com.minhthien.hoser_backend.dto.request.BetMarketRequest;
import com.minhthien.hoser_backend.dto.request.BetRequest;
import com.minhthien.hoser_backend.dto.response.BetMarketResponse;
import com.minhthien.hoser_backend.dto.response.BetResponse;

import java.util.List;

public interface BettingService {
    BetMarketResponse createBetMarket(Long adminId, Long raceId, BetMarketRequest request);

    BetMarketResponse openBetMarket(Long adminId, Long marketId);

    BetMarketResponse closeBetMarket(Long adminId, Long marketId);

    List<BetMarketResponse> getAdminBetMarkets(Long adminId);

    List<BetResponse> getAdminMarketBets(Long adminId, Long marketId);

    List<BetResponse> getAdminBets(Long adminId, Long raceId);

    BetMarketResponse getPublicOpenBetMarket(Long raceId);

    List<BetMarketResponse> getBettableRaceMarkets(Long userId);

    BetResponse placeBet(Long userId, Long raceId, BetRequest request);

    List<BetResponse> getUserBets(Long userId);

    BetResponse getUserBet(Long userId, Long betId);

    void lockRaceBets(Long raceId);

    void settleRaceBets(Long raceId);

    void cancelRaceBets(Long raceId);
}
