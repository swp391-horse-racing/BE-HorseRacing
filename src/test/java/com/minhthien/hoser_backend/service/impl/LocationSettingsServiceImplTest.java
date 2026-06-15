package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.ProvinceRequest;
import com.minhthien.hoser_backend.dto.request.RaceVenueRequest;
import com.minhthien.hoser_backend.entity.Province;
import com.minhthien.hoser_backend.entity.RaceVenue;
import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.repository.ProvinceRepository;
import com.minhthien.hoser_backend.repository.RaceRepository;
import com.minhthien.hoser_backend.repository.RaceVenueRepository;
import com.minhthien.hoser_backend.repository.TournamentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationSettingsServiceImplTest {
    @Mock private ProvinceRepository provinceRepository;
    @Mock private RaceVenueRepository raceVenueRepository;
    @Mock private TournamentRepository tournamentRepository;
    @Mock private RaceRepository raceRepository;

    @InjectMocks
    private LocationSettingsServiceImpl service;

    @Test
    void createProvinceNormalizesCodeAndSaves() {
        ProvinceRequest request = new ProvinceRequest();
        request.setName("Ho Chi Minh City");
        request.setCode("hcm");
        when(provinceRepository.save(any(Province.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createProvince(request);

        assertEquals("HCM", response.getCode());
        assertEquals("Ho Chi Minh City", response.getName());
    }

    @Test
    void deleteProvinceRejectsConfiguredVenuesOrTournamentUsage() {
        Province province = province();
        when(provinceRepository.findById(50L)).thenReturn(Optional.of(province));
        when(raceVenueRepository.existsByProvinceId(50L)).thenReturn(true);

        assertThrows(BadRequestException.class, () -> service.deleteProvince(50L));

        when(raceVenueRepository.existsByProvinceId(50L)).thenReturn(false);
        when(tournamentRepository.existsByProvinceId(50L)).thenReturn(true);

        assertThrows(BadRequestException.class, () -> service.deleteProvince(50L));
        verify(provinceRepository, never()).delete(any());
    }

    @Test
    void deactivateProvinceRejectsActiveTournamentUsage() {
        Province province = province();
        when(provinceRepository.findById(50L)).thenReturn(Optional.of(province));
        when(tournamentRepository.existsByProvinceIdAndStatusIn(any(), anyCollection())).thenReturn(true);

        assertThrows(BadRequestException.class, () -> service.updateProvinceActive(50L, false));
    }

    @Test
    void updateProvinceCannotBypassDeactivateRule() {
        ProvinceRequest request = new ProvinceRequest();
        request.setName("Ho Chi Minh City");
        request.setCode("HCM");
        request.setActive(false);
        when(provinceRepository.findById(50L)).thenReturn(Optional.of(province()));
        when(tournamentRepository.existsByProvinceIdAndStatusIn(any(), anyCollection())).thenReturn(true);

        assertThrows(BadRequestException.class, () -> service.updateProvince(50L, request));
        verify(provinceRepository, never()).save(any());
    }

    @Test
    void createVenueRequiresActiveProvinceAndSavesAddress() {
        RaceVenueRequest request = new RaceVenueRequest();
        request.setName("Phu Tho Racecourse");
        request.setAddress("District 11");
        when(provinceRepository.findById(50L)).thenReturn(Optional.of(province()));
        when(raceVenueRepository.save(any(RaceVenue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createVenue(50L, request);

        assertEquals("Phu Tho Racecourse", response.getName());
        assertEquals("District 11", response.getAddress());
        assertEquals(50L, response.getProvinceId());
    }

    @Test
    void deleteVenueRejectsRaceUsage() {
        RaceVenue venue = venue();
        when(raceVenueRepository.findById(60L)).thenReturn(Optional.of(venue));
        when(raceRepository.existsByVenueId(60L)).thenReturn(true);

        assertThrows(BadRequestException.class, () -> service.deleteVenue(60L));
        verify(raceVenueRepository, never()).delete(any());
    }

    @Test
    void tournamentVenueOptionsReturnOnlyActiveVenuesForTournamentProvince() {
        Tournament tournament = Tournament.builder()
                .id(3L)
                .province(province())
                .build();
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));
        when(raceVenueRepository.findByProvinceIdAndActiveTrueOrderByNameAsc(50L))
                .thenReturn(List.of(venue()));

        var response = service.getActiveVenuesByTournament(3L);

        assertEquals(List.of(60L), response.stream().map(item -> item.getId()).toList());
    }

    private Province province() {
        return Province.builder()
                .id(50L)
                .name("Ho Chi Minh City")
                .code("HCM")
                .active(true)
                .build();
    }

    private RaceVenue venue() {
        return RaceVenue.builder()
                .id(60L)
                .province(province())
                .name("Phu Tho Racecourse")
                .address("District 11")
                .active(true)
                .build();
    }
}
