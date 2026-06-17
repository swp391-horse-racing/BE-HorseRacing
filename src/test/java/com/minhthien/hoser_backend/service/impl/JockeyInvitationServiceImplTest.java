package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.InvitationDecisionRequest;
import com.minhthien.hoser_backend.dto.request.JockeyInvitationRequest;
import com.minhthien.hoser_backend.dto.response.JockeyInvitationResponse;
import com.minhthien.hoser_backend.entity.Horse;
import com.minhthien.hoser_backend.entity.JockeyInvitation;
import com.minhthien.hoser_backend.entity.JockeyProfile;
import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.AssignmentStatus;
import com.minhthien.hoser_backend.enums.HorseStatus;
import com.minhthien.hoser_backend.enums.JockeyStatus;
import com.minhthien.hoser_backend.enums.RaceStatus;
import com.minhthien.hoser_backend.enums.TournamentStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.exception.DuplicateResourceException;
import com.minhthien.hoser_backend.repository.HorseRepository;
import com.minhthien.hoser_backend.repository.JockeyInvitationRepository;
import com.minhthien.hoser_backend.repository.JockeyProfileRepository;
import com.minhthien.hoser_backend.repository.RaceRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    private RaceRepository raceRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private JockeyInvitationServiceImpl service;

    @Test
    void createInvitationDoesNotRequireJockeyHirePrice() {
        User owner = owner();
        User jockey = jockey();
        Horse horse = horse(owner);
        Race race = race();
        JockeyProfile profile = profile(jockey);
        JockeyInvitationRequest request = invitationRequest(horse, jockey, "500000.00");

        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(userRepository.findById(jockey.getId())).thenReturn(Optional.of(jockey));
        when(horseRepository.findByIdAndOwnerId(horse.getId(), owner.getId())).thenReturn(Optional.of(horse));
        when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        when(jockeyProfileRepository.findByUserId(jockey.getId())).thenReturn(Optional.of(profile));
        when(jockeyInvitationRepository.existsByRaceIdAndHorseIdAndStatusIn(
                eq(race.getId()), eq(horse.getId()), anyCollection())).thenReturn(false);
        when(jockeyInvitationRepository.save(any(JockeyInvitation.class))).thenAnswer(invocation -> {
            JockeyInvitation invitation = invocation.getArgument(0);
            invitation.setId(100L);
            return invitation;
        });

        JockeyInvitationResponse response = service.createInvitation(owner.getId(), request);

        assertEquals(AssignmentStatus.PENDING, response.getStatus());
        assertEquals("Please join my horse team", response.getMessage());
        assertEquals(new BigDecimal("500000.00"), response.getRemunerationAmount());
        assertEquals(race.getId(), response.getRaceId());
        assertEquals("Race 1", response.getRaceName());
        assertEquals(9L, response.getTournamentId());
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
        assertEquals("Jockey accepted a conflicting invitation", otherPending.getResponseNote());
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
    void createInvitationRejectsJockeyWhoAlreadyAcceptedSameOrOverlappingRace() {
        User owner = owner();
        User jockey = jockey();
        Horse horse = horse(owner);
        Race race = race();
        JockeyProfile profile = profile(jockey);
        JockeyInvitationRequest request = invitationRequest(horse, jockey, "700000.00");

        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(userRepository.findById(jockey.getId())).thenReturn(Optional.of(jockey));
        when(horseRepository.findByIdAndOwnerId(horse.getId(), owner.getId())).thenReturn(Optional.of(horse));
        when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        when(jockeyProfileRepository.findByUserId(jockey.getId())).thenReturn(Optional.of(profile));
        when(jockeyInvitationRepository.existsAcceptedJockeyRaceConflict(
                eq(jockey.getId()), eq(race.getId()), eq(race.getScheduledStartAt()),
                eq(race.getScheduledEndAt()), eq(AssignmentStatus.ACCEPTED), anyCollection(), anyCollection()))
                .thenReturn(true);

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.createInvitation(owner.getId(), request));

        assertEquals("Jockey already accepted an invitation for this race or an overlapping race",
                exception.getMessage());
    }

    @Test
    void createInvitationAllowsWhenAcceptedRaceIsFinishedOrDoesNotOverlap() {
        User owner = owner();
        User jockey = jockey();
        Horse horse = horse(owner);
        Race race = race();
        JockeyProfile profile = profile(jockey);
        JockeyInvitationRequest request = invitationRequest(horse, jockey, "700000.00");

        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(userRepository.findById(jockey.getId())).thenReturn(Optional.of(jockey));
        when(horseRepository.findByIdAndOwnerId(horse.getId(), owner.getId())).thenReturn(Optional.of(horse));
        when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        when(jockeyProfileRepository.findByUserId(jockey.getId())).thenReturn(Optional.of(profile));
        when(jockeyInvitationRepository.existsAcceptedJockeyRaceConflict(
                eq(jockey.getId()), eq(race.getId()), eq(race.getScheduledStartAt()),
                eq(race.getScheduledEndAt()), eq(AssignmentStatus.ACCEPTED), anyCollection(), anyCollection()))
                .thenReturn(false);
        when(jockeyInvitationRepository.existsByRaceIdAndHorseIdAndStatusIn(
                eq(race.getId()), eq(horse.getId()), anyCollection())).thenReturn(false);
        when(jockeyInvitationRepository.save(any(JockeyInvitation.class))).thenAnswer(invocation -> {
            JockeyInvitation invitation = invocation.getArgument(0);
            invitation.setId(102L);
            return invitation;
        });

        JockeyInvitationResponse response = service.createInvitation(owner.getId(), request);

        assertEquals(AssignmentStatus.PENDING, response.getStatus());
    }

    @Test
    void createInvitationAllowsMultiplePendingInvitationsForSameJockey() {
        User owner = owner();
        User jockey = jockey();
        Horse horse = horse(owner);
        Race race = race();
        JockeyProfile profile = profile(jockey);
        JockeyInvitationRequest request = invitationRequest(horse, jockey, "800000.00");

        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(userRepository.findById(jockey.getId())).thenReturn(Optional.of(jockey));
        when(horseRepository.findByIdAndOwnerId(horse.getId(), owner.getId())).thenReturn(Optional.of(horse));
        when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        when(jockeyProfileRepository.findByUserId(jockey.getId())).thenReturn(Optional.of(profile));
        when(jockeyInvitationRepository.existsByRaceIdAndHorseIdAndStatusIn(
                eq(race.getId()), eq(horse.getId()), anyCollection())).thenReturn(false);
        when(jockeyInvitationRepository.save(any(JockeyInvitation.class))).thenAnswer(invocation -> {
            JockeyInvitation invitation = invocation.getArgument(0);
            invitation.setId(101L);
            return invitation;
        });

        JockeyInvitationResponse response = service.createInvitation(owner.getId(), request);

        assertEquals(AssignmentStatus.PENDING, response.getStatus());
        assertEquals(new BigDecimal("800000.00"), response.getRemunerationAmount());
    }

    @Test
    void createInvitationRejectsWhenHorseAlreadyHasActiveInvitationInRace() {
        User owner = owner();
        User jockey = jockey();
        Horse horse = horse(owner);
        Race race = race();
        JockeyProfile profile = profile(jockey);
        JockeyInvitationRequest request = invitationRequest(horse, jockey, "800000.00");

        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(userRepository.findById(jockey.getId())).thenReturn(Optional.of(jockey));
        when(horseRepository.findByIdAndOwnerId(horse.getId(), owner.getId())).thenReturn(Optional.of(horse));
        when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        when(jockeyProfileRepository.findByUserId(jockey.getId())).thenReturn(Optional.of(profile));
        when(jockeyInvitationRepository.existsByRaceIdAndHorseIdAndStatusIn(
                eq(race.getId()), eq(horse.getId()), anyCollection())).thenReturn(true);

        DuplicateResourceException exception = assertThrows(
                DuplicateResourceException.class,
                () -> service.createInvitation(owner.getId(), request));

        assertEquals("Active invitation already exists for this horse in this race", exception.getMessage());
    }

    @Test
    void acceptInvitationCancelsOverlappingPendingInvitationsAndKeepsDifferentTimePending() {
        User jockey = jockey();
        JockeyInvitation invitation = pendingInvitation(owner(), jockey,
                race(7L, LocalDateTime.of(2026, 7, 20, 10, 0),
                        LocalDateTime.of(2026, 7, 20, 11, 0), RaceStatus.PUBLISHED,
                        TournamentStatus.OPEN_REGISTRATION));
        JockeyInvitation overlappingPending = pendingInvitation(owner(5L, "overlap-owner"), jockey,
                race(8L, LocalDateTime.of(2026, 7, 20, 10, 30),
                        LocalDateTime.of(2026, 7, 20, 11, 30), RaceStatus.PUBLISHED,
                        TournamentStatus.OPEN_REGISTRATION));
        overlappingPending.setId(11L);
        JockeyInvitation differentTimePending = pendingInvitation(owner(6L, "later-owner"), jockey,
                race(9L, LocalDateTime.of(2026, 7, 20, 11, 0),
                        LocalDateTime.of(2026, 7, 20, 12, 0), RaceStatus.PUBLISHED,
                        TournamentStatus.OPEN_REGISTRATION));
        differentTimePending.setId(12L);
        JockeyInvitation finishedRacePending = pendingInvitation(owner(7L, "finished-owner"), jockey,
                race(10L, LocalDateTime.of(2026, 7, 20, 10, 30),
                        LocalDateTime.of(2026, 7, 20, 11, 30), RaceStatus.RESULT_CONFIRMED,
                        TournamentStatus.OPEN_REGISTRATION));
        finishedRacePending.setId(13L);

        when(userRepository.findById(jockey.getId())).thenReturn(Optional.of(jockey));
        when(jockeyInvitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(jockeyInvitationRepository.save(any(JockeyInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jockeyInvitationRepository.findByJockeyIdAndStatusAndIdNotOrderByCreatedAtDesc(
                jockey.getId(), AssignmentStatus.PENDING, invitation.getId()))
                .thenReturn(List.of(overlappingPending, differentTimePending, finishedRacePending));
        when(jockeyInvitationRepository.saveAll(anyCollection())).thenAnswer(invocation -> invocation.getArgument(0));

        service.acceptInvitation(jockey.getId(), invitation.getId(), null);

        assertEquals(AssignmentStatus.CANCELLED, overlappingPending.getStatus());
        assertEquals("Jockey accepted a conflicting invitation", overlappingPending.getResponseNote());
        assertEquals(AssignmentStatus.PENDING, differentTimePending.getStatus());
        assertEquals(AssignmentStatus.PENDING, finishedRacePending.getStatus());
        verify(jockeyInvitationRepository).saveAll(List.of(overlappingPending));
    }

    private JockeyInvitation pendingInvitation(User owner, User jockey) {
        return pendingInvitation(owner, jockey, race());
    }

    private JockeyInvitation pendingInvitation(User owner, User jockey, Race race) {
        Horse horse = horse(owner);
        return JockeyInvitation.builder()
                .id(10L)
                .owner(owner)
                .jockey(jockey)
                .horse(horse)
                .race(race)
                .jockeyProfile(profile(jockey))
                .status(AssignmentStatus.PENDING)
                .message("Invite")
                .remunerationAmount(new BigDecimal("500000.00"))
                .build();
    }

    private Race race() {
        return race(7L, LocalDateTime.of(2026, 7, 20, 10, 0),
                LocalDateTime.of(2026, 7, 20, 11, 0), RaceStatus.PUBLISHED,
                TournamentStatus.OPEN_REGISTRATION);
    }

    private Race race(Long id, LocalDateTime scheduledStartAt, LocalDateTime scheduledEndAt,
                      RaceStatus raceStatus, TournamentStatus tournamentStatus) {
        return Race.builder()
                .id(id)
                .name("Race 1")
                .scheduledStartAt(scheduledStartAt)
                .scheduledEndAt(scheduledEndAt)
                .status(raceStatus)
                .tournament(Tournament.builder()
                        .id(9L)
                        .name("Summer Cup")
                        .status(tournamentStatus)
                        .build())
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
        request.setRaceId(7L);
        request.setJockeyId(jockey.getId());
        request.setMessage("Please join my horse team");
        request.setRemunerationAmount(new BigDecimal(remunerationAmount));
        return request;
    }
}
