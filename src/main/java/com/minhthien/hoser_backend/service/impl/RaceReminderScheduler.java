package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RaceReminderLog;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.RaceStatus;
import com.minhthien.hoser_backend.repository.RaceReminderLogRepository;
import com.minhthien.hoser_backend.repository.RaceRepository;
import com.minhthien.hoser_backend.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class RaceReminderScheduler {
    static final String REMINDER_3_DAYS = "REMINDER_3_DAYS";

    private final RaceRepository raceRepository;
    private final RaceReminderLogRepository raceReminderLogRepository;
    private final MailService mailService;

    @Scheduled(
            initialDelayString = "${app.race-reminder.initial-delay-ms:60000}",
            fixedDelayString = "${app.race-reminder.delay-ms:3600000}"
    )
    @Transactional
    public void sendThreeDayRaceReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime upperBound = now.plusDays(3);
        int sent = 0;
        for (Race race : raceRepository.findByStatusAndScheduledStartAtBetweenOrderByScheduledStartAtAsc(
                RaceStatus.SCHEDULED, now, upperBound)) {
            for (User recipient : recipientsFor(race)) {
                if (sendOnce(race, recipient, REMINDER_3_DAYS)) {
                    sent++;
                }
            }
        }
        if (sent > 0) {
            log.info("Sent race reminders: count={}", sent);
        }
    }

    private boolean sendOnce(Race race, User recipient, String eventType) {
        if (recipient == null || recipient.getId() == null
                || raceReminderLogRepository.existsByRaceIdAndRecipientIdAndEventType(
                race.getId(), recipient.getId(), eventType)) {
            return false;
        }
        mailService.sendRaceReminder(race, recipient);
        raceReminderLogRepository.save(RaceReminderLog.builder()
                .race(race)
                .recipient(recipient)
                .eventType(eventType)
                .build());
        return true;
    }

    private Set<User> recipientsFor(Race race) {
        Set<User> recipients = new LinkedHashSet<>();
        race.getParticipants().forEach(participant -> {
            recipients.add(participant.getOwner());
            recipients.add(participant.getJockey());
        });
        recipients.add(race.getReferee());
        recipients.remove(null);
        return recipients;
    }
}
