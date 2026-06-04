package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.response.EligibleHorseTeamResponse;
import com.minhthien.hoser_backend.entity.JockeyInvitation;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.AssignmentStatus;
import com.minhthien.hoser_backend.enums.HorseStatus;
import com.minhthien.hoser_backend.enums.JockeyStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.exception.ResourceNotFoundException;
import com.minhthien.hoser_backend.exception.UnauthorizedException;
import com.minhthien.hoser_backend.repository.JockeyInvitationRepository;
import com.minhthien.hoser_backend.repository.TournamentRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.HorseTeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HorseTeamServiceImpl implements HorseTeamService {
    private final JockeyInvitationRepository jockeyInvitationRepository;
    private final TournamentRepository tournamentRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<EligibleHorseTeamResponse> getOwnerEligibleHorseTeams(Long ownerId) {
        User owner = requireUser(ownerId);
        requireRole(owner, UserRole.OWNER, "Only owners can view eligible horse teams");
        return jockeyInvitationRepository.findByOwnerIdAndStatusOrderByCreatedAtDesc(
                        ownerId, AssignmentStatus.ACCEPTED).stream()
                .filter(this::isEligible)
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EligibleHorseTeamResponse> getAdminTournamentEligibleHorseTeams(Long adminId, Long tournamentId) {
        User admin = requireUser(adminId);
        requireRole(admin, UserRole.ADMIN, "Only admins can view eligible horse teams");
        if (!tournamentRepository.existsById(tournamentId)) {
            throw new ResourceNotFoundException("Tournament", "id", tournamentId);
        }
        return jockeyInvitationRepository.findByStatusOrderByCreatedAtDesc(AssignmentStatus.ACCEPTED).stream()
                .filter(this::isEligible)
                .map(this::mapToResponse)
                .toList();
    }

    private boolean isEligible(JockeyInvitation invitation) {
        return invitation.getStatus() == AssignmentStatus.ACCEPTED
                && invitation.getHorse().getStatus() == HorseStatus.APPROVED
                && invitation.getJockeyProfile().getStatus() == JockeyStatus.APPROVED
                && invitation.getHorse().getOwner().getId().equals(invitation.getOwner().getId());
    }

    private EligibleHorseTeamResponse mapToResponse(JockeyInvitation invitation) {
        return EligibleHorseTeamResponse.builder()
                .invitationId(invitation.getId())
                .horseId(invitation.getHorse().getId())
                .horseName(invitation.getHorse().getName())
                .ownerId(invitation.getOwner().getId())
                .ownerUsername(invitation.getOwner().getUsername())
                .jockeyId(invitation.getJockey().getId())
                .jockeyUsername(invitation.getJockey().getUsername())
                .jockeyProfileId(invitation.getJockeyProfile().getId())
                .jockeyFullName(invitation.getJockeyProfile().getUser().getFullName())
                .acceptedAt(invitation.getRespondedAt())
                .build();
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private void requireRole(User user, UserRole role, String message) {
        if (user.getRole() != role) {
            throw new UnauthorizedException(message);
        }
    }
}
