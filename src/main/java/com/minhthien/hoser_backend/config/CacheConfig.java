package com.minhthien.hoser_backend.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
                "adminTournamentSummaries",
                "publicTournamentSummaries",
                "adminTournamentDetails",
                "publicTournamentDetails",
                "publicTournamentRaces",
                "adminNewsSummaries",
                "publicNewsSummaries",
                "newsDetails"
        );
    }
}
