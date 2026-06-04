package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.InvitationDecisionRequest;
import com.minhthien.hoser_backend.dto.request.JockeyInvitationRequest;
import com.minhthien.hoser_backend.dto.response.JockeyInvitationResponse;
import com.minhthien.hoser_backend.entity.Horse;
import com.minhthien.hoser_backend.entity.JockeyInvitation;
import com.minhthien.hoser_backend.entity.JockeyProfile;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.AssignmentStatus;
import com.minhthien.hoser_backend.enums.HorseStatus;
import com.minhthien.hoser_backend.enums.JockeyStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.repository.HorseRepository;
import com.minhthien.hoser_backend.repository.JockeyInvitationRepository;
import com.minhthien.hoser_backend.repository.JockeyProfileRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import java.math.BigDecimal;
import java.util.List;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JockeyInvitationServiceImplTest {
    @Mock
    private JockeyInvitationRepository jockeyInvitationRepository;
    @Mock
    private HorseRepository horseRepository;
    @Mock
    private JockeyProfileRepository jockeyProfileRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private JockeyInvitationServiceImpl service;

    @Test
    void createInvitationDoesNotRequireJockeyHirePrice() {
        User owner = owner();
        User jockey = jockey();
        Horse horse = horse(owner);
        JockeyProfile profile = profile(jockey);
        JockeyInvitationRequest request = invitationRequest(horse, jockey, "500000.00");

        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(userRepository.findById(jockey.getId())).thenReturn(Optional.of(jockey));
        when(horseRepository.findByIdAndOwnerId(horse.getId(), owner.getId())).thenReturn(Optional.of(horse));
        when(jockeyProfileRepository.findByUserId(jockey.getId())).thenReturn(Optional.of(profile));
        when(jockeyInvitationRepository.existsByJockeyIdAndStatus(jockey.getId(), AssignmentStatus.ACCEPTED))
                .thenReturn(false);
        when(jockeyInvitationRepository.existsByHorseIdAndJockeyIdAndStatusIn(
                eq(horse.getId()), eq(jockey.getId()), anyCollection())).thenReturn(false);
        when(jockeyInvitationRepository.save(any(JockeyInvitation.class))).thenAnswer(invocation -> {
            JockeyInvitation invitation = invocation.getArgument(0);
            invitation.setId(100L);
            return invitation;
        });

        JockeyInvitationResponse response = service.createInvitation(owner.getId(), request);

        assertEquals(AssignmentStatus.PENDING, response.getStatus());
        assertEquals("Please join my horse team", response.getMessage());
        assertEquals(new BigDecimal("500000.00"), response.getRemunerationAmount());
        verify(jockeyInvitationRepository).save(any(JockeyInvitation.class));
    }

    @Test
    void acceptInvitationMarksSelectedAcceptedAndCancelsOtherPendingInvitations() {
        User jockey = jockey();
        JockeyInvitation invitation = pendingInvitation(owner(), jockey);
        JockeyInvitation otherPending = pendingInvitation(owner(5L, "other-owner"), jockey);
        otherPending.setId(11L);
        JockeyInvitation alreadyRejected = pendingInvitation(owner(6L, "rejected-owner"), jockey);
        alreadyRejected.setId(12L);
        alreadyRejected.setStatus(AssignmentStatus.REJECTED);
        InvitationDecisionRequest request = new InvitationDecisionRequest();
        request.setNote("Accepted");

        when(userRepository.findById(jockey.getId())).thenReturn(Optional.of(jockey));
        when(jockeyInvitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(jockeyInvitationRepository.save(any(JockeyInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jockeyInvitationRepository.findByJockeyIdAndStatusAndIdNotOrderByCreatedAtDesc(
                jockey.getId(), AssignmentStatus.PENDING, invitation.getId())).thenReturn(List.of(otherPending));
        when(jockeyInvitationRepository.saveAll(anyCollection())).thenAnswer(invocation -> invocation.getArgument(0));

        JockeyInvitationResponse response = service.acceptInvitation(jockey.getId(), invitation.getId(), request);

        assertEquals(AssignmentStatus.ACCEPTED, response.getStatus());
        assertEquals("Accepted", response.getResponseNote());
        assertNotNull(response.getRespondedAt());
        assertEquals(AssignmentStatus.CANCELLED, otherPending.getStatus());
        assertEquals("Jockey accepted another invitation", otherPending.getResponseNote());
        assertNotNull(otherPending.getCancelledAt());
        assertEquals(AssignmentStatus.REJECTED, alreadyRejected.getStatus());
        verify(jockeyInvitationRepository).saveAll(List.of(otherPending));
    }

    @Test
    void rejectInvitationOnlyMarksInvitationRejected() {
        User jockey = jockey();
        JockeyInvitation invitation = pendingInvitation(owner(), jockey);
        InvitationDecisionRequest request = new InvitationDecisionRequest();
        request.setNote("Busy");

        when(userRepository.findById(jockey.getId())).thenReturn(Optional.of(jockey));
        when(jockeyInvitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(jockeyInvitationRepository.save(any(JockeyInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JockeyInvitationResponse response = service.rejectInvitation(jockey.getId(), invitation.getId(), request);

        assertEquals(AssignmentStatus.REJECTED, response.getStatus());
        assertEquals("Busy", response.getResponseNote());
        assertNotNull(response.getRespondedAt());
    }

    @Test
    void cancelInvitationOnlyMarksInvitationCancelled() {
        User owner = owner();
        JockeyInvitation invitation = pendingInvitation(owner, jockey());

        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(jockeyInvitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(jockeyInvitationRepository.save(any(JockeyInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JockeyInvitationResponse response = service.cancelInvitation(owner.getId(), invitation.getId());

        assertEquals(AssignmentStatus.CANCELLED, response.getStatus());
        assertNotNull(response.getCancelledAt());
    }

    @Test
    void createInvitationRejectsJockeyWhoAlreadyAcceptedAnotherInvitation() {
        User owner = owner();
        User jockey = jockey();
        Horse horse = horse(owner);
        JockeyProfile profile = profile(jockey);
        JockeyInvitationRequest request = invitationRequest(horse, jockey, "700000.00");

        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(userRepository.findById(jockey.getId())).thenReturn(Optional.of(jockey));
        when(horseRepository.findByIdAndOwnerId(horse.getId(), owner.getId())).thenReturn(Optional.of(horse));
        when(jockeyProfileRepository.findByUserId(jockey.getId())).thenReturn(Optional.of(profile));
        when(jockeyInvitationRepository.existsByJockeyIdAndStatus(jockey.getId(), AssignmentStatus.ACCEPTED))
                .thenReturn(true);

        org.junit.jupiter.api.Assertions.assertThrows(
                com.minhthien.hoser_backend.exception.BadRequestException.class,
                () -> service.createInvitation(owner.getId(), request));
    }

    @Test
    void createInvitationAllowsMultiplePendingInvitationsForSameJockey() {
        User owner = owner();
        User jockey = jockey();
        Horse horse = horse(owner);
        JockeyProfile profile = profile(jockey);
        JockeyInvitationRequest request = invitationRequest(horse, jockey, "800000.00");

        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(userRepository.findById(jockey.getId())).thenReturn(Optional.of(jockey));
        when(horseRepository.findByIdAndOwnerId(horse.getId(), owner.getId())).thenReturn(Optional.of(horse));
        when(jockeyProfileRepository.findByUserId(jockey.getId())).thenReturn(Optional.of(profile));
        when(jockeyInvitationRepository.existsByJockeyIdAndStatus(jockey.getId(), AssignmentStatus.ACCEPTED))
                .thenReturn(false);
        when(jockeyInvitationRepository.existsByHorseIdAndJockeyIdAndStatusIn(
                eq(horse.getId()), eq(jockey.getId()), anyCollection())).thenReturn(false);
        when(jockeyInvitationRepository.save(any(JockeyInvitation.class))).thenAnswer(invocation -> {
            JockeyInvitation invitation = invocation.getArgument(0);
            invitation.setId(101L);
            return invitation;
        });

        JockeyInvitationResponse response = service.createInvitation(owner.getId(), request);

        assertEquals(AssignmentStatus.PENDING, response.getStatus());
        assertEquals(new BigDecimal("800000.00"), response.getRemunerationAmount());
    }

    private JockeyInvitation pendingInvitation(User owner, User jockey) {
        Horse horse = horse(owner);
        return JockeyInvitation.builder()
                .id(10L)
                .owner(owner)
                .jockey(jockey)
                .horse(horse)
                .jockeyProfile(profile(jockey))
                .status(AssignmentStatus.PENDING)
                .message("Invite")
                .remunerationAmount(new BigDecimal("500000.00"))
                .build();
    }

    private User owner() {
        return owner(1L, "owner");
    }

    private User owner(Long id, String username) {
        return User.builder()
                .id(id)
                .username(username)
                .role(UserRole.OWNER)
                .build();
    }

    private User jockey() {
        return User.builder()
                .id(2L)
                .username("jockey")
                .role(UserRole.JOCKEY)
                .build();
    }

    private Horse horse(User owner) {
        return Horse.builder()
                .id(3L)
                .owner(owner)
                .name("Lightning")
                .status(HorseStatus.APPROVED)
                .build();
    }

    private JockeyProfile profile(User jockey) {
        return JockeyProfile.builder()
                .id(4L)
                .user(jockey)
                .status(JockeyStatus.APPROVED)
                .build();
    }

    private JockeyInvitationRequest invitationRequest(Horse horse, User jockey, String remunerationAmount) {
        JockeyInvitationRequest request = new JockeyInvitationRequest();
        request.setHorseId(horse.getId());
        request.setJockeyId(jockey.getId());
        request.setMessage("Please join my horse team");
        request.setRemunerationAmount(new BigDecimal(remunerationAmount));
        return request;
    }
}
