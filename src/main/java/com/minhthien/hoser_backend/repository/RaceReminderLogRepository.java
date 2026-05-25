package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.RaceReminderLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RaceReminderLogRepository extends JpaRepository<RaceReminderLog, Long> {
    boolean existsByRaceIdAndRecipientIdAndEventType(Long raceId, Long recipientId, String eventType);
}
