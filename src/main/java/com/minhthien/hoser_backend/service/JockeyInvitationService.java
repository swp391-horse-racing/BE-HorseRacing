package com.minhthien.hoser_backend.service;

import com.minhthien.hoser_backend.dto.request.InvitationDecisionRequest;
import com.minhthien.hoser_backend.dto.request.JockeyInvitationRequest;
import com.minhthien.hoser_backend.dto.response.JockeyInvitationResponse;
import com.minhthien.hoser_backend.entity.User;

import java.util.List;

public interface JockeyInvitationService {
    JockeyInvitationResponse createInvitation(Long ownerId, JockeyInvitationRequest request);

    List<JockeyInvitationResponse> getOwnerInvitations(Long ownerId);

    JockeyInvitationResponse getOwnerInvitation(Long ownerId, Long invitationId);

    List<JockeyInvitationResponse> getOwnerAcceptedJockeys(Long ownerId);

    JockeyInvitationResponse cancelInvitation(Long ownerId, Long invitationId);

    List<JockeyInvitationResponse> getJockeyInvitations(Long jockeyId);

    JockeyInvitationResponse getJockeyInvitation(Long jockeyId, Long invitationId);

    JockeyInvitationResponse acceptInvitation(Long jockeyId, Long invitationId, InvitationDecisionRequest request);

    JockeyInvitationResponse rejectInvitation(Long jockeyId, Long invitationId, InvitationDecisionRequest request);

    List<User> cancelActiveInvitationsForRace(Long raceId, String reason, String updatedBy);
}
