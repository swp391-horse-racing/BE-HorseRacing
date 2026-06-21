package com.minhthien.hoser_backend.service;

import com.minhthien.hoser_backend.dto.response.RankingResponse;

public interface RankingService {
    RankingResponse getRankings(int limit);
}
