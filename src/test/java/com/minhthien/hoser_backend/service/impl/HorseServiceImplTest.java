package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.HorseRequest;
import com.minhthien.hoser_backend.dto.request.HorseUpdateRequest;
import com.minhthien.hoser_backend.dto.response.HorseResponse;
import com.minhthien.hoser_backend.entity.Horse;
import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RaceResult;
import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.HorseGender;
import com.minhthien.hoser_backend.enums.HorseStatus;
import com.minhthien.hoser_backend.enums.RaceParticipantStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.repository.HorseRepository;
import com.minhthien.hoser_backend.repository.JockeyInvitationRepository;
import com.minhthien.hoser_backend.repository.RaceParticipantRepository;
import com.minhthien.hoser_backend.repository.RaceRegistrationRepository;
import com.minhthien.hoser_backend.repository.RaceResultRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.CloudinaryUploadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.core.convert.ConversionFailedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HorseServiceImplTest {
    @Mock
    private HorseRepository horseRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CloudinaryUploadService cloudinaryUploadService;
    @Mock
    private JockeyInvitationRepository jockeyInvitationRepository;
    @Mock
    private RaceRegistrationRepository raceRegistrationRepository;
    @Mock
    private RaceParticipantRepository raceParticipantRepository;
    @Mock
    private RaceResultRepository raceResultRepository;

    @InjectMocks
    private HorseServiceImpl service;

    @Test
    void createHorseStoresMaleGenderEnum() {
        User owner = owner();
        HorseRequest request = new HorseRequest();
        request.setName("Lightning");
        request.setGender(HorseGender.MALE);
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(horseRepository.save(any(Horse.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HorseResponse response = service.createHorse(owner.getId(), request, null, null);

        ArgumentCaptor<Horse> horseCaptor = ArgumentCaptor.forClass(Horse.class);
        verify(horseRepository).save(horseCaptor.capture());
        assertEquals(HorseGender.MALE, horseCaptor.getValue().getGender());
        assertEquals(HorseGender.MALE, response.getGender());
    }

    @Test
    void updateHorseChangesGenderToFemaleEnum() {
        User owner = owner();
        Horse horse = horse(10L);
        horse.setStatus(HorseStatus.PENDING);
        horse.setGender(HorseGender.MALE);
        HorseUpdateRequest request = new HorseUpdateRequest();
        request.setGender(HorseGender.FEMALE);
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(horseRepository.findByIdAndOwnerId(horse.getId(), owner.getId())).thenReturn(Optional.of(horse));
        when(horseRepository.save(horse)).thenReturn(horse);
        when(raceResultRepository.findByHorseIdOrderByRaceScheduledStartAtDesc(horse.getId())).thenReturn(List.of());

        HorseResponse response = service.updateHorse(owner.getId(), horse.getId(), request, null, null);

        assertEquals(HorseGender.FEMALE, horse.getGender());
        assertEquals(HorseGender.FEMALE, response.getGender());
    }

    @Test
    void updateHorseKeepsExistingGenderWhenGenderIsOmitted() {
        User owner = owner();
        Horse horse = horse(10L);
        horse.setStatus(HorseStatus.PENDING);
        horse.setGender(HorseGender.MALE);
        HorseUpdateRequest request = new HorseUpdateRequest();
        request.setBreed("Arabian");
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(horseRepository.findByIdAndOwnerId(horse.getId(), owner.getId())).thenReturn(Optional.of(horse));
        when(horseRepository.save(horse)).thenReturn(horse);
        when(raceResultRepository.findByHorseIdOrderByRaceScheduledStartAtDesc(horse.getId())).thenReturn(List.of());

        HorseResponse response = service.updateHorse(owner.getId(), horse.getId(), request, null, null);

        assertEquals(HorseGender.MALE, horse.getGender());
        assertEquals(HorseGender.MALE, response.getGender());
    }

    @Test
    void invalidHorseGenderCannotBeConvertedToEnum() {
        assertThrows(ConversionFailedException.class, () ->
                ApplicationConversionService.getSharedInstance().convert("OTHER", HorseGender.class));
    }

    @Test
    void horseDetailIncludesEmptyPerformanceWhenHorseHasNoResults() {
        Horse horse = horse(10L);
        when(horseRepository.findById(10L)).thenReturn(Optional.of(horse));
        when(raceResultRepository.findByHorseIdOrderByRaceScheduledStartAtDesc(10L)).thenReturn(List.of());

        HorseResponse response = service.getHorseForViewer(null, 10L);

        assertEquals(0, response.getPerformance().getTotalRaces());
        assertEquals(0, response.getPerformance().getWins());
        assertEquals(new BigDecimal("0.00"), response.getPerformance().getWinRate());
        assertTrue(response.getPerformance().getRankCounts().isEmpty());
        assertTrue(response.getRaceHistory().isEmpty());
    }

    @Test
    void horseDetailIncludesPerformanceAndRaceHistoryFromResults() {
        Horse horse = horse(10L);
        List<RaceResult> results = List.of(
                result(1L, horse, 1, LocalDateTime.of(2026, 7, 15, 10, 0)),
                result(2L, horse, 1, LocalDateTime.of(2026, 7, 14, 10, 0)),
                result(3L, horse, 2, LocalDateTime.of(2026, 7, 13, 10, 0)),
                result(4L, horse, 5, LocalDateTime.of(2026, 7, 12, 10, 0)),
                result(5L, horse, null, LocalDateTime.of(2026, 7, 11, 10, 0))
        );
        when(horseRepository.findById(10L)).thenReturn(Optional.of(horse));
        when(raceResultRepository.findByHorseIdOrderByRaceScheduledStartAtDesc(10L)).thenReturn(results);

        HorseResponse response = service.getHorseForViewer(null, 10L);

        assertEquals(5, response.getPerformance().getTotalRaces());
        assertEquals(2, response.getPerformance().getWins());
        assertEquals(new BigDecimal("40.00"), response.getPerformance().getWinRate());
        assertEquals(2L, response.getPerformance().getRankCounts().get("1"));
        assertEquals(1L, response.getPerformance().getRankCounts().get("2"));
        assertEquals(1L, response.getPerformance().getRankCounts().get("5"));
        assertEquals(3, response.getPerformance().getRankCounts().size());
        assertEquals(5, response.getRaceHistory().size());
        assertEquals(1L, response.getRaceHistory().get(0).getTournamentId());
        assertEquals("Summer Cup", response.getRaceHistory().get(0).getTournamentName());
        assertEquals(101L, response.getRaceHistory().get(0).getRaceId());
        assertEquals("Race 1", response.getRaceHistory().get(0).getRaceName());
        assertEquals(1, response.getRaceHistory().get(0).getRank());
        assertEquals(RaceParticipantStatus.FINISHED, response.getRaceHistory().get(0).getStatus());
        assertEquals(61000L, response.getRaceHistory().get(0).getFinishTimeMillis());
        assertEquals(LocalDateTime.of(2026, 7, 15, 12, 0), response.getRaceHistory().get(0).getFinalizedAt());
    }

    private Horse horse(Long id) {
        return Horse.builder()
                .id(id)
                .owner(owner())
                .name("Lightning")
                .status(HorseStatus.APPROVED)
                .build();
    }

    private User owner() {
        return User.builder()
                .id(20L)
                .username("owner")
                .role(UserRole.OWNER)
                .build();
    }

    private RaceResult result(Long id, Horse horse, Integer rank, LocalDateTime scheduledStartAt) {
        Tournament tournament = Tournament.builder()
                .id(1L)
                .name("Summer Cup")
                .build();
        Race race = Race.builder()
                .id(100L + id)
                .tournament(tournament)
                .name("Race " + id)
                .scheduledStartAt(scheduledStartAt)
                .build();
        return RaceResult.builder()
                .id(id)
                .race(race)
                .horse(horse)
                .rank(rank)
                .status(RaceParticipantStatus.FINISHED)
                .finishTimeMillis(60000L + id * 1000)
                .finalizedAt(LocalDateTime.of(2026, 7, 15, 12, 0))
                .build();
    }
}
