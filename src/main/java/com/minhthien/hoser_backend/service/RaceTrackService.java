package com.minhthien.hoser_backend.service;

import com.minhthien.hoser_backend.dto.request.RaceTrackRequest;
import com.minhthien.hoser_backend.dto.response.RaceTrackResponse;

import java.util.List;

public interface RaceTrackService {
    List<RaceTrackResponse> getRaceTracks(Long adminId, String locationKey, Boolean active);

    RaceTrackResponse createRaceTrack(Long adminId, RaceTrackRequest request);

    RaceTrackResponse updateRaceTrack(Long adminId, Long raceTrackId, RaceTrackRequest request);

    RaceTrackResponse updateRaceTrackActive(Long adminId, Long raceTrackId, Boolean active);
}
