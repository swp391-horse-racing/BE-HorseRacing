package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.enums.RaceStatus;
import com.minhthien.hoser_backend.enums.TournamentStatus;

final class TournamentStatusSync {
    private TournamentStatusSync() {
    }

    static void syncPreRaceStatuses(Tournament tournament, TournamentStatus tournamentStatus) {
        if (tournament.getRaces() == null) {
            return;
        }
        RaceStatus raceStatus = preRaceStatusFor(tournamentStatus);
        if (raceStatus == null) {
            return;
        }
        tournament.getRaces().stream()
                .filter(race -> isPreRaceStatus(race.getStatus()))
                .forEach(race -> race.setStatus(raceStatus));
    }

    private static RaceStatus preRaceStatusFor(TournamentStatus tournamentStatus) {
        return switch (tournamentStatus) {
            case DRAFT -> RaceStatus.DRAFT;
            case PUBLISHED -> RaceStatus.PUBLISHED;
            case OPEN_REGISTRATION -> RaceStatus.OPEN_REGISTRATION;
            case REGISTRATION_CLOSED -> RaceStatus.REGISTRATION_CLOSED;
            case SCHEDULED -> RaceStatus.SCHEDULED;
            default -> null;
        };
    }

    private static boolean isPreRaceStatus(RaceStatus status) {
        return status == RaceStatus.DRAFT
                || status == RaceStatus.PUBLISHED
                || status == RaceStatus.OPEN_REGISTRATION
                || status == RaceStatus.REGISTRATION_CLOSED;
    }
}
