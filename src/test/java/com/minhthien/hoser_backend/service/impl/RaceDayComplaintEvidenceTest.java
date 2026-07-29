package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.RaceComplaintRequest;
import com.minhthien.hoser_backend.entity.Horse;
import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RaceParticipant;
import com.minhthien.hoser_backend.entity.RaceRegistration;
import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.RaceParticipantStatus;
import com.minhthien.hoser_backend.enums.RaceStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.repository.JockeyChallengeResultRepository;
import com.minhthien.hoser_backend.repository.JockeyInvitationRepository;
import com.minhthien.hoser_backend.repository.RaceComplaintRepository;
import com.minhthien.hoser_backend.repository.RaceParticipantRepository;
import com.minhthien.hoser_backend.repository.RaceRegistrationRepository;
import com.minhthien.hoser_backend.repository.RaceRepository;
import com.minhthien.hoser_backend.repository.RaceResultRepository;
import com.minhthien.hoser_backend.repository.TournamentRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.BettingService;
import com.minhthien.hoser_backend.service.CloudinaryUploadService;
import com.minhthien.hoser_backend.service.FinanceSettingsService;
import com.minhthien.hoser_backend.service.MailService;
import com.minhthien.hoser_backend.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaceDayComplaintEvidenceTest {
    private static final String EVIDENCE_FOLDER = "hoser/race-complaints/evidence";

    @Mock private RaceRepository raceRepository;
    @Mock private RaceRegistrationRepository raceRegistrationRepository;
    @Mock private RaceParticipantRepository raceParticipantRepository;
    @Mock private RaceResultRepository raceResultRepository;
    @Mock private RaceComplaintRepository raceComplaintRepository;
    @Mock private JockeyChallengeResultRepository jockeyChallengeResultRepository;
    @Mock private JockeyInvitationRepository jockeyInvitationRepository;
    @Mock private TournamentRepository tournamentRepository;
    @Mock private UserRepository userRepository;
    @Mock private WalletService walletService;
    @Mock private TournamentServiceImpl tournamentService;
    @Mock private FinanceSettingsService financeSettingsService;
    @Mock private MailService mailService;
    @Mock private BettingService bettingService;
    @Mock private CloudinaryUploadService cloudinaryUploadService;

    @InjectMocks
    private RaceDayServiceImpl service;

    @Test
    void createComplaintUploadsImageEvidenceAndStoresReturnedUrl() {
        Fixture fixture = fixture();
        RaceComplaintRequest request = request(fixture.accusedParticipant.getId());
        MockMultipartFile evidence = new MockMultipartFile(
                "evidence", "finish-line.jpg", "image/jpeg", "image".getBytes());
        when(cloudinaryUploadService.uploadImage(evidence, EVIDENCE_FOLDER))
                .thenReturn("https://cdn.example/evidence.jpg");
        stubValidComplaint(fixture);
        when(raceComplaintRepository.save(any())).thenAnswer(invocation -> {
            var complaint = (com.minhthien.hoser_backend.entity.RaceComplaint) invocation.getArgument(0);
            complaint.setId(99L);
            return complaint;
        });

        var response = service.createRaceComplaint(
                fixture.complainant.getId(), fixture.race.getId(), request, evidence);

        assertEquals("https://cdn.example/evidence.jpg", response.getEvidenceUrl());
        ArgumentCaptor<com.minhthien.hoser_backend.entity.RaceComplaint> complaintCaptor =
                ArgumentCaptor.forClass(com.minhthien.hoser_backend.entity.RaceComplaint.class);
        verify(raceComplaintRepository).save(complaintCaptor.capture());
        assertEquals("https://cdn.example/evidence.jpg", complaintCaptor.getValue().getEvidenceUrl());
    }

    @Test
    void createComplaintWithoutEvidenceKeepsEvidenceUrlNull() {
        Fixture fixture = fixture();
        RaceComplaintRequest request = request(fixture.accusedParticipant.getId());
        stubValidComplaint(fixture);
        when(raceComplaintRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createRaceComplaint(
                fixture.complainant.getId(), fixture.race.getId(), request, null);

        assertNull(response.getEvidenceUrl());
        verify(cloudinaryUploadService, never()).uploadImage(any(), anyString());
    }

    @Test
    void createComplaintDoesNotSaveWhenEvidenceUploadFails() {
        Fixture fixture = fixture();
        RaceComplaintRequest request = request(fixture.accusedParticipant.getId());
        MockMultipartFile evidence = new MockMultipartFile(
                "evidence", "notes.txt", "text/plain", "not image".getBytes());
        stubValidComplaint(fixture);
        when(cloudinaryUploadService.uploadImage(evidence, EVIDENCE_FOLDER))
                .thenThrow(new BadRequestException("Only image files are allowed"));

        assertThrows(BadRequestException.class, () -> service.createRaceComplaint(
                fixture.complainant.getId(), fixture.race.getId(), request, evidence));
        verify(raceComplaintRepository, never()).save(any());
    }

    private void stubValidComplaint(Fixture fixture) {
        when(userRepository.findById(fixture.complainant.getId())).thenReturn(Optional.of(fixture.complainant));
        when(raceRepository.findById(fixture.race.getId())).thenReturn(Optional.of(fixture.race));
        when(raceParticipantRepository.findById(fixture.accusedParticipant.getId()))
                .thenReturn(Optional.of(fixture.accusedParticipant));
        when(raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(fixture.race.getId()))
                .thenReturn(List.of(fixture.complainantParticipant, fixture.accusedParticipant));
    }

    private RaceComplaintRequest request(Long accusedParticipantId) {
        RaceComplaintRequest request = new RaceComplaintRequest();
        request.setAccusedParticipantId(accusedParticipantId);
        request.setReason("Blocked the lane near finish line");
        return request;
    }

    private Fixture fixture() {
        User complainant = user(1L, "owner-a", UserRole.OWNER);
        User accusedOwner = user(2L, "owner-b", UserRole.OWNER);
        User jockey = user(3L, "jockey", UserRole.JOCKEY);
        Tournament tournament = Tournament.builder().id(10L).name("Cup").build();
        Race race = Race.builder()
                .id(20L)
                .name("Race 1")
                .tournament(tournament)
                .status(RaceStatus.RESULT_CONFIRMED)
                .resultFinalizedAt(LocalDateTime.now().minusHours(2))
                .build();
        RaceParticipant complainantParticipant = participant(30L, race, complainant, jockey, "Thunder");
        RaceParticipant accusedParticipant = participant(31L, race, accusedOwner, jockey, "Lightning");
        return new Fixture(complainant, accusedParticipant, complainantParticipant, race);
    }

    private RaceParticipant participant(Long id, Race race, User owner, User jockey, String horseName) {
        return RaceParticipant.builder()
                .id(id)
                .race(race)
                .registration(RaceRegistration.builder().id(id + 100).build())
                .owner(owner)
                .horse(Horse.builder().id(id + 200).name(horseName).owner(owner).build())
                .jockey(jockey)
                .gateNumber(id.intValue())
                .status(RaceParticipantStatus.CHECKED_IN)
                .build();
    }

    private User user(Long id, String username, UserRole role) {
        return User.builder()
                .id(id)
                .username(username)
                .email(username + "@example.test")
                .role(role)
                .build();
    }

    private record Fixture(
            User complainant,
            RaceParticipant accusedParticipant,
            RaceParticipant complainantParticipant,
            Race race
    ) {
    }
}
