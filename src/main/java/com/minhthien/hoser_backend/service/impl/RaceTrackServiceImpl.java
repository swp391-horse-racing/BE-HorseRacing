package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.RaceTrackRequest;
import com.minhthien.hoser_backend.dto.response.RaceTrackResponse;
import com.minhthien.hoser_backend.entity.RaceTrack;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.exception.ResourceNotFoundException;
import com.minhthien.hoser_backend.exception.UnauthorizedException;
import com.minhthien.hoser_backend.repository.RaceTrackRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.RaceTrackService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RaceTrackServiceImpl implements RaceTrackService {
    private final RaceTrackRepository raceTrackRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<RaceTrackResponse> getRaceTracks(Long adminId, String locationKey, Boolean active) {
        requireAdmin(adminId);
        String normalizedLocationKey = normalizeLocationKey(locationKey);
        List<RaceTrack> tracks;
        if (hasText(normalizedLocationKey) && active != null) {
            tracks = raceTrackRepository.findByLocationKeyAndActiveOrderByNameAsc(normalizedLocationKey, active);
        } else if (hasText(normalizedLocationKey)) {
            tracks = raceTrackRepository.findByLocationKeyOrderByNameAsc(normalizedLocationKey);
        } else if (active != null) {
            tracks = raceTrackRepository.findByActiveOrderByLocationKeyAscNameAsc(active);
        } else {
            tracks = raceTrackRepository.findAllByOrderByLocationKeyAscNameAsc();
        }
        return tracks.stream().map(this::mapToResponse).toList();
    }

    @Override
    @Transactional
    public RaceTrackResponse createRaceTrack(Long adminId, RaceTrackRequest request) {
        requireAdmin(adminId);
        validateRequest(request);
        RaceTrack track = RaceTrack.builder().build();
        applyRequest(track, request);
        return mapToResponse(raceTrackRepository.save(track));
    }

    @Override
    @Transactional
    public RaceTrackResponse updateRaceTrack(Long adminId, Long raceTrackId, RaceTrackRequest request) {
        requireAdmin(adminId);
        validateRequest(request);
        RaceTrack track = requireRaceTrack(raceTrackId);
        applyRequest(track, request);
        return mapToResponse(raceTrackRepository.save(track));
    }

    @Override
    @Transactional
    public RaceTrackResponse updateRaceTrackActive(Long adminId, Long raceTrackId, Boolean active) {
        requireAdmin(adminId);
        if (active == null) {
            throw new BadRequestException("Race track active status is required");
        }
        RaceTrack track = requireRaceTrack(raceTrackId);
        track.setActive(active);
        return mapToResponse(raceTrackRepository.save(track));
    }

    private void applyRequest(RaceTrack track, RaceTrackRequest request) {
        track.setName(request.getName());
        track.setLocationKey(normalizeLocationKey(request.getLocationKey()));
        track.setLocationName(request.getLocationName());
        track.setAddress(request.getAddress());
        track.setTrackType(request.getTrackType());
        track.setDistance(request.getDistance());
        track.setActive(request.getActive() == null || request.getActive());
    }

    private void validateRequest(RaceTrackRequest request) {
        if (request == null) {
            throw new BadRequestException("Race track request is required");
        }
        if (!hasText(request.getName())) {
            throw new BadRequestException("Race track name is required");
        }
        if (!hasText(request.getLocationKey())) {
            throw new BadRequestException("Location key is required");
        }
        if (!hasText(request.getLocationName())) {
            throw new BadRequestException("Location name is required");
        }
    }

    private RaceTrack requireRaceTrack(Long raceTrackId) {
        return raceTrackRepository.findById(raceTrackId)
                .orElseThrow(() -> new ResourceNotFoundException("RaceTrack", "id", raceTrackId));
    }

    private User requireAdmin(Long adminId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", adminId));
        if (admin.getRole() != UserRole.ADMIN) {
            throw new UnauthorizedException("Only admins can manage race tracks");
        }
        return admin;
    }

    private RaceTrackResponse mapToResponse(RaceTrack track) {
        return RaceTrackResponse.builder()
                .id(track.getId())
                .name(track.getName())
                .locationKey(track.getLocationKey())
                .locationName(track.getLocationName())
                .address(track.getAddress())
                .trackType(track.getTrackType())
                .distance(track.getDistance())
                .active(track.getActive())
                .createdAt(track.getCreatedAt())
                .updatedAt(track.getUpdatedAt())
                .build();
    }

    private String normalizeLocationKey(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
