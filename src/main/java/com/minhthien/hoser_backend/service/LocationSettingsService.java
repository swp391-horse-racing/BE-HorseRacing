package com.minhthien.hoser_backend.service;

import com.minhthien.hoser_backend.dto.request.ProvinceRequest;
import com.minhthien.hoser_backend.dto.request.RaceVenueRequest;
import com.minhthien.hoser_backend.dto.response.ProvinceResponse;
import com.minhthien.hoser_backend.dto.response.RaceVenueResponse;
import com.minhthien.hoser_backend.entity.Province;
import com.minhthien.hoser_backend.entity.RaceVenue;

import java.util.List;

public interface LocationSettingsService {
    List<ProvinceResponse> getProvinces();

    ProvinceResponse createProvince(ProvinceRequest request);

    ProvinceResponse updateProvince(Long id, ProvinceRequest request);

    ProvinceResponse updateProvinceActive(Long id, boolean active);

    void deleteProvince(Long id);

    List<RaceVenueResponse> getVenuesByProvince(Long provinceId);

    List<RaceVenueResponse> getActiveVenuesByTournament(Long tournamentId);

    RaceVenueResponse createVenue(Long provinceId, RaceVenueRequest request);

    RaceVenueResponse updateVenue(Long venueId, RaceVenueRequest request);

    RaceVenueResponse updateVenueActive(Long venueId, boolean active);

    void deleteVenue(Long venueId);

    Province requireActiveProvince(Long provinceId);

    RaceVenue requireActiveVenue(Long venueId);

    RaceVenueResponse mapVenue(RaceVenue venue);
}
