package com.minhthien.hoser_backend.service;

import com.minhthien.hoser_backend.dto.request.InvitationDecisionRequest;
import com.minhthien.hoser_backend.dto.request.RefereeInvitationRequest;
import com.minhthien.hoser_backend.dto.response.RefereeInvitationResponse;
import com.minhthien.hoser_backend.entity.User;

import java.util.List;

public interface RefereeInvitationService {
    RefereeInvitationResponse createInvitation(Long adminId, RefereeInvitationRequest request);

    List<RefereeInvitationResponse> getAdminInvitations(Long adminId);

    RefereeInvitationResponse getAdminInvitation(Long adminId, Long invitationId);

    RefereeInvitationResponse cancelInvitation(Long adminId, Long invitationId);

    List<RefereeInvitationResponse> getRefereeInvitations(Long refereeId);

    RefereeInvitationResponse getRefereeInvitation(Long refereeId, Long invitationId);

    RefereeInvitationResponse acceptInvitation(Long refereeId, Long invitationId, InvitationDecisionRequest request);

    RefereeInvitationResponse rejectInvitation(Long refereeId, Long invitationId, InvitationDecisionRequest request);

    void cancelPendingInvitationsForRace(Long raceId, String reason);

    List<User> cancelActiveInvitationsForRace(Long raceId, String reason, String updatedBy);
}
