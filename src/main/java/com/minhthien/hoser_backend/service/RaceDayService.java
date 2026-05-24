package com.minhthien.hoser_backend.service;

import com.minhthien.hoser_backend.dto.request.RaceFinalizeResultRequest;
import com.minhthien.hoser_backend.dto.request.RaceRegistrationRequest;
import com.minhthien.hoser_backend.dto.request.RaceRegistrationReviewRequest;
import com.minhthien.hoser_backend.dto.request.RaceRegistrationWithdrawRequest;
import com.minhthien.hoser_backend.dto.response.JockeyChallengeStandingResponse;
import com.minhthien.hoser_backend.dto.response.RaceRegistrationResponse;
import com.minhthien.hoser_backend.dto.response.RaceResponse;
import com.minhthien.hoser_backend.dto.response.RaceResultResponse;

import java.util.List;

public interface RaceDayService {
    RaceRegistrationResponse registerForRace(Long ownerId, Long raceId, RaceRegistrationRequest request);

    List<RaceRegistrationResponse> getOwnerRaceRegistrations(Long ownerId);

    List<RaceRegistrationResponse> getAdminTournamentRaceRegistrations(Long adminId, Long tournamentId);

    RaceRegistrationResponse approveRaceRegistration(Long adminId, Long registrationId,
                                                     RaceRegistrationReviewRequest request);

    RaceRegistrationResponse rejectRaceRegistration(Long adminId, Long registrationId,
                                                    RaceRegistrationReviewRequest request);

    RaceRegistrationResponse withdrawRaceRegistration(Long ownerId, Long registrationId,
                                                      RaceRegistrationWithdrawRequest request);

    List<RaceResponse> getRefereeRaces(Long refereeId);

    List<RaceResultResponse> finalizeRaceResult(Long refereeId, Long raceId, RaceFinalizeResultRequest request);

    List<RaceResultResponse> getRaceResults(Long raceId);

    List<JockeyChallengeStandingResponse> finalizeJockeyChallenge(Long adminId, Long tournamentId);

    List<JockeyChallengeStandingResponse> getJockeyChallengeStandings(Long tournamentId);
}
