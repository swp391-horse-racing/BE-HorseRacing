package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.response.NotificationResponse;
import com.minhthien.hoser_backend.dto.response.RealtimeEventResponse;
import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.service.RealtimeEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeEventServiceImpl implements RealtimeEventService {
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void publishRaceStatus(Race race, String eventType, String status, String referenceId) {
        if (race == null || race.getId() == null) {
            return;
        }
        publish("/topic/races/%d/status".formatted(race.getId()), event(race, eventType, status, referenceId));
    }

    @Override
    public void publishRaceResult(Race race, String eventType, String referenceId) {
        if (race == null || race.getId() == null) {
            return;
        }
        publish("/topic/races/%d/results".formatted(race.getId()), event(race, eventType, null, referenceId));
    }

    @Override
    public void publishTournamentLeaderboard(Long tournamentId, String eventType, String referenceId) {
        if (tournamentId == null) {
            return;
        }
        RealtimeEventResponse event = RealtimeEventResponse.builder()
                .eventType(eventType)
                .tournamentId(tournamentId)
                .referenceId(referenceId)
                .timestamp(LocalDateTime.now())
                .build();
        publish("/topic/tournaments/%d/leaderboard".formatted(tournamentId), event);
    }

    @Override
    public void publishUserNotification(Long userId, NotificationResponse notification) {
        if (userId == null || notification == null) {
            return;
        }
        try {
            messagingTemplate.convertAndSendToUser(notification.getRecipientUsername(), "/queue/notifications", notification);
        } catch (RuntimeException ex) {
            log.warn("Could not publish user notification: userId={}, notificationId={}",
                    userId, notification.getId(), ex);
        }
    }

    private void publish(String destination, RealtimeEventResponse event) {
        try {
            messagingTemplate.convertAndSend(destination, event);
        } catch (RuntimeException ex) {
            log.warn("Could not publish websocket event: destination={}, eventType={}",
                    destination, event.getEventType(), ex);
        }
    }

    private RealtimeEventResponse event(Race race, String eventType, String status, String referenceId) {
        return RealtimeEventResponse.builder()
                .eventType(eventType)
                .raceId(race.getId())
                .tournamentId(race.getTournament() == null ? null : race.getTournament().getId())
                .status(status)
                .referenceId(referenceId)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
