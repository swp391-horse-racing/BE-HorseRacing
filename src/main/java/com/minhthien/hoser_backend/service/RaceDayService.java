package com.minhthien.hoser_backend.service;

import com.minhthien.hoser_backend.dto.request.RaceFinalizeResultRequest;
import com.minhthien.hoser_backend.dto.request.RaceCancellationRequest;
import com.minhthien.hoser_backend.dto.request.RaceComplaintRequest;
import com.minhthien.hoser_backend.dto.request.RaceComplaintResolveRequest;
import com.minhthien.hoser_backend.dto.request.RaceGateUpdateRequest;
import com.minhthien.hoser_backend.dto.request.RaceParticipantCheckInRequest;
import com.minhthien.hoser_backend.dto.request.RaceRegistrationRequest;
import com.minhthien.hoser_backend.dto.request.RaceRegistrationReviewRequest;
import com.minhthien.hoser_backend.dto.request.RaceRegistrationWithdrawRequest;
import com.minhthien.hoser_backend.dto.request.RaceRefereeAssignmentRequest;
import com.minhthien.hoser_backend.dto.response.JockeyChallengeStandingResponse;
import com.minhthien.hoser_backend.dto.response.RaceComplaintResponse;
import com.minhthien.hoser_backend.dto.response.RaceParticipantResponse;
import com.minhthien.hoser_backend.dto.response.RaceRegistrationResponse;
import com.minhthien.hoser_backend.dto.response.RaceResponse;
import com.minhthien.hoser_backend.dto.response.RaceResultResponse;
import com.minhthien.hoser_backend.dto.response.TournamentResponse;

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

    TournamentResponse scheduleTournament(Long adminId, Long tournamentId);

    List<RaceParticipantResponse> getRaceParticipants(Long adminId, Long raceId);

    RaceParticipantResponse updateParticipantGate(Long adminId, Long raceId, Long participantId,
                                                  RaceGateUpdateRequest request);

    RaceResponse assignRaceReferee(Long adminId, Long raceId, RaceRefereeAssignmentRequest request);

    RaceResponse cancelRace(Long adminId, Long raceId, RaceCancellationRequest request);

    List<RaceResponse> getRefereeRaces(Long refereeId);

    List<RaceParticipantResponse> getRefereeRaceParticipants(Long refereeId, Long raceId);

    RaceParticipantResponse checkInRaceParticipant(Long refereeId, Long raceId, Long participantId,
                                                   RaceParticipantCheckInRequest request);

    RaceResponse startRace(Long refereeId, Long raceId);

    List<RaceResultResponse> finalizeRaceResult(Long refereeId, Long raceId, RaceFinalizeResultRequest request);

    List<RaceResultResponse> getRaceResults(Long raceId);

    RaceComplaintResponse createRaceComplaint(Long ownerId, Long raceId, RaceComplaintRequest request);

    List<RaceComplaintResponse> getOwnerRaceComplaints(Long ownerId);

    List<RaceComplaintResponse> getAdminRaceComplaints(Long adminId,
                                                       com.minhthien.hoser_backend.enums.RaceComplaintStatus status);

    RaceComplaintResponse resolveRaceComplaint(Long adminId, Long complaintId, RaceComplaintResolveRequest request);

    List<JockeyChallengeStandingResponse> finalizeJockeyChallenge(Long adminId, Long tournamentId);

    List<JockeyChallengeStandingResponse> getJockeyChallengeStandings(Long tournamentId);
}
