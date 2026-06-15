package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.ProvinceRequest;
import com.minhthien.hoser_backend.dto.request.RaceVenueRequest;
import com.minhthien.hoser_backend.dto.response.ProvinceResponse;
import com.minhthien.hoser_backend.dto.response.RaceVenueResponse;
import com.minhthien.hoser_backend.entity.Province;
import com.minhthien.hoser_backend.entity.RaceVenue;
import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.enums.TournamentStatus;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.exception.DuplicateResourceException;
import com.minhthien.hoser_backend.exception.ResourceNotFoundException;
import com.minhthien.hoser_backend.repository.ProvinceRepository;
import com.minhthien.hoser_backend.repository.RaceRepository;
import com.minhthien.hoser_backend.repository.RaceVenueRepository;
import com.minhthien.hoser_backend.repository.TournamentRepository;
import com.minhthien.hoser_backend.service.LocationSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LocationSettingsServiceImpl implements LocationSettingsService {
    private static final Set<TournamentStatus> INACTIVE_BLOCKING_STATUSES = Set.of(
            TournamentStatus.DRAFT,
            TournamentStatus.PUBLISHED,
            TournamentStatus.OPEN_REGISTRATION,
            TournamentStatus.REGISTRATION_CLOSED,
            TournamentStatus.SCHEDULED,
            TournamentStatus.ONGOING
    );

    private final ProvinceRepository provinceRepository;
    private final RaceVenueRepository raceVenueRepository;
    private final TournamentRepository tournamentRepository;
    private final RaceRepository raceRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ProvinceResponse> getProvinces() {
        return provinceRepository.findAllByOrderByNameAsc().stream()
                .map(this::mapProvince)
                .toList();
    }

    @Override
    @Transactional
    public ProvinceResponse createProvince(ProvinceRequest request) {
        validateProvinceUniqueness(request.getName(), request.getCode(), null);
        Province province = Province.builder()
                .name(normalizeText(request.getName(), "Province name"))
                .code(normalizeCode(request.getCode()))
                .active(!Boolean.FALSE.equals(request.getActive()))
                .build();
        return mapProvince(provinceRepository.save(province));
    }

    @Override
    @Transactional
    @CacheEvict(value = {
            "adminTournamentSummaries",
            "publicTournamentSummaries",
            "adminTournamentDetails",
            "publicTournamentDetails",
            "publicTournamentRaces"
    }, allEntries = true)
    public ProvinceResponse updateProvince(Long id, ProvinceRequest request) {
        Province province = requireProvince(id);
        validateProvinceUniqueness(request.getName(), request.getCode(), id);
        province.setName(normalizeText(request.getName(), "Province name"));
        province.setCode(normalizeCode(request.getCode()));
        boolean active = !Boolean.FALSE.equals(request.getActive());
        if (!active) {
            requireProvinceCanDeactivate(id);
        }
        province.setActive(active);
        return mapProvince(provinceRepository.save(province));
    }

    @Override
    @Transactional
    public ProvinceResponse updateProvinceActive(Long id, boolean active) {
        Province province = requireProvince(id);
        if (!active) {
            requireProvinceCanDeactivate(id);
        }
        province.setActive(active);
        return mapProvince(provinceRepository.save(province));
    }

    @Override
    @Transactional
    public void deleteProvince(Long id) {
        Province province = requireProvince(id);
        if (raceVenueRepository.existsByProvinceId(id)) {
            throw new BadRequestException("Cannot delete province with configured venues");
        }
        if (tournamentRepository.existsByProvinceId(id)) {
            throw new BadRequestException("Cannot delete province used by tournaments");
        }
        provinceRepository.delete(province);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceVenueResponse> getVenuesByProvince(Long provinceId) {
        requireProvince(provinceId);
        return raceVenueRepository.findByProvinceIdOrderByNameAsc(provinceId).stream()
                .map(this::mapVenue)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceVenueResponse> getActiveVenuesByTournament(Long tournamentId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", tournamentId));
        if (tournament.getProvince() == null) {
            throw new BadRequestException("Tournament province is required");
        }
        return raceVenueRepository.findByProvinceIdAndActiveTrueOrderByNameAsc(tournament.getProvince().getId())
                .stream()
                .map(this::mapVenue)
                .toList();
    }

    @Override
    @Transactional
    public RaceVenueResponse createVenue(Long provinceId, RaceVenueRequest request) {
        Province province = requireActiveProvince(provinceId);
        validateVenueUniqueness(provinceId, request.getName(), null);
        RaceVenue venue = RaceVenue.builder()
                .province(province)
                .name(normalizeText(request.getName(), "Venue name"))
                .address(normalizeOptionalText(request.getAddress()))
                .active(!Boolean.FALSE.equals(request.getActive()))
                .build();
        return mapVenue(raceVenueRepository.save(venue));
    }

    @Override
    @Transactional
    @CacheEvict(value = {
            "adminTournamentDetails",
            "publicTournamentDetails",
            "publicTournamentRaces"
    }, allEntries = true)
    public RaceVenueResponse updateVenue(Long venueId, RaceVenueRequest request) {
        RaceVenue venue = requireVenue(venueId);
        validateVenueUniqueness(venue.getProvince().getId(), request.getName(), venueId);
        venue.setName(normalizeText(request.getName(), "Venue name"));
        venue.setAddress(normalizeOptionalText(request.getAddress()));
        venue.setActive(!Boolean.FALSE.equals(request.getActive()));
        return mapVenue(raceVenueRepository.save(venue));
    }

    @Override
    @Transactional
    public RaceVenueResponse updateVenueActive(Long venueId, boolean active) {
        RaceVenue venue = requireVenue(venueId);
        venue.setActive(active);
        return mapVenue(raceVenueRepository.save(venue));
    }

    @Override
    @Transactional
    public void deleteVenue(Long venueId) {
        RaceVenue venue = requireVenue(venueId);
        if (raceRepository.existsByVenueId(venueId)) {
            throw new BadRequestException("Cannot delete venue used by races");
        }
        raceVenueRepository.delete(venue);
    }

    @Override
    @Transactional(readOnly = true)
    public Province requireActiveProvince(Long provinceId) {
        Province province = requireProvince(provinceId);
        if (!Boolean.TRUE.equals(province.getActive())) {
            throw new BadRequestException("Province is inactive");
        }
        return province;
    }

    @Override
    @Transactional(readOnly = true)
    public RaceVenue requireActiveVenue(Long venueId) {
        RaceVenue venue = requireVenue(venueId);
        if (!Boolean.TRUE.equals(venue.getActive())) {
            throw new BadRequestException("Race venue is inactive");
        }
        if (venue.getProvince() == null || !Boolean.TRUE.equals(venue.getProvince().getActive())) {
            throw new BadRequestException("Race venue province is inactive");
        }
        return venue;
    }

    @Override
    public RaceVenueResponse mapVenue(RaceVenue venue) {
        Province province = venue.getProvince();
        return RaceVenueResponse.builder()
                .id(venue.getId())
                .provinceId(province == null ? null : province.getId())
                .provinceName(province == null ? null : province.getName())
                .name(venue.getName())
                .address(venue.getAddress())
                .active(venue.getActive())
                .createdAt(venue.getCreatedAt())
                .updatedAt(venue.getUpdatedAt())
                .build();
    }

    private Province requireProvince(Long id) {
        if (id == null) {
            throw new BadRequestException("Province is required");
        }
        return provinceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Province", "id", id));
    }

    private RaceVenue requireVenue(Long id) {
        if (id == null) {
            throw new BadRequestException("Race venue is required");
        }
        return raceVenueRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Race venue", "id", id));
    }

    private void validateProvinceUniqueness(String name, String code, Long existingId) {
        String normalizedName = normalizeText(name, "Province name");
        String normalizedCode = normalizeCode(code);
        if (existingId == null) {
            if (provinceRepository.existsByNameIgnoreCase(normalizedName)) {
                throw new DuplicateResourceException("Province name already exists");
            }
            if (provinceRepository.existsByCodeIgnoreCase(normalizedCode)) {
                throw new DuplicateResourceException("Province code already exists");
            }
            return;
        }
        if (provinceRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, existingId)) {
            throw new DuplicateResourceException("Province name already exists");
        }
        if (provinceRepository.existsByCodeIgnoreCaseAndIdNot(normalizedCode, existingId)) {
            throw new DuplicateResourceException("Province code already exists");
        }
    }

    private void requireProvinceCanDeactivate(Long id) {
        if (tournamentRepository.existsByProvinceIdAndStatusIn(id, INACTIVE_BLOCKING_STATUSES)) {
            throw new BadRequestException("Cannot deactivate province used by active tournaments");
        }
    }

    private void validateVenueUniqueness(Long provinceId, String name, Long existingId) {
        String normalizedName = normalizeText(name, "Venue name");
        if (existingId == null) {
            if (raceVenueRepository.existsByProvinceIdAndNameIgnoreCase(provinceId, normalizedName)) {
                throw new DuplicateResourceException("Venue name already exists in province");
            }
            return;
        }
        if (raceVenueRepository.existsByProvinceIdAndNameIgnoreCaseAndIdNot(provinceId, normalizedName, existingId)) {
            throw new DuplicateResourceException("Venue name already exists in province");
        }
    }

    private ProvinceResponse mapProvince(Province province) {
        return ProvinceResponse.builder()
                .id(province.getId())
                .name(province.getName())
                .code(province.getCode())
                .active(province.getActive())
                .createdAt(province.getCreatedAt())
                .updatedAt(province.getUpdatedAt())
                .build();
    }

    private String normalizeText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(label + " is required");
        }
        return value.trim();
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeCode(String code) {
        return normalizeText(code, "Province code").toUpperCase();
    }
}
