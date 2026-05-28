package com.minhthien.hoser_backend.service;

import com.minhthien.hoser_backend.dto.response.NotificationResponse;
import com.minhthien.hoser_backend.entity.Race;

public interface RealtimeEventService {
    void publishRaceStatus(Race race, String eventType, String status, String referenceId);

    void publishRaceResult(Race race, String eventType, String referenceId);

    void publishTournamentLeaderboard(Long tournamentId, String eventType, String referenceId);

    void publishUserNotification(Long userId, NotificationResponse notification);
}
