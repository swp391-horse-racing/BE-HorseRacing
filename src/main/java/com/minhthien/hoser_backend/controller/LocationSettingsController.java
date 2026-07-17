package com.minhthien.hoser_backend.controller;

import com.minhthien.hoser_backend.dto.request.ProvinceRequest;
import com.minhthien.hoser_backend.dto.request.RaceVenueRequest;
import com.minhthien.hoser_backend.dto.response.ApiResponse;
import com.minhthien.hoser_backend.dto.response.ProvinceResponse;
import com.minhthien.hoser_backend.dto.response.RaceVenueResponse;
import com.minhthien.hoser_backend.service.LocationSettingsService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class LocationSettingsController {
    private final LocationSettingsService locationSettingsService;

    @GetMapping("/provinces")
    public ResponseEntity<ApiResponse<List<ProvinceResponse>>> getProvinces() {
        return ResponseEntity.ok(ApiResponse.success(locationSettingsService.getProvinces()));
    }

    @PostMapping("/provinces")
    public ResponseEntity<ApiResponse<ProvinceResponse>> createProvince(
            @Valid @RequestBody ProvinceRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Province created",
                locationSettingsService.createProvince(request)));
    }

    @PutMapping("/provinces/{id}")
    public ResponseEntity<ApiResponse<ProvinceResponse>> updateProvince(
            @PathVariable Long id,
            @Valid @RequestBody ProvinceRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Province updated",
                locationSettingsService.updateProvince(id, request)));
    }

    @DeleteMapping("/provinces/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProvince(@PathVariable Long id) {
        locationSettingsService.deleteProvince(id);
        return ResponseEntity.ok(ApiResponse.success("Province deleted", null));
    }

    @PutMapping("/provinces/{id}/active")
    public ResponseEntity<ApiResponse<ProvinceResponse>> updateProvinceActive(
            @PathVariable Long id,
            @RequestParam boolean active) {
        return ResponseEntity.ok(ApiResponse.success("Province active status updated",
                locationSettingsService.updateProvinceActive(id, active)));
    }

    @GetMapping("/provinces/{provinceId}/venues")
    public ResponseEntity<ApiResponse<List<RaceVenueResponse>>> getVenuesByProvince(
            @PathVariable Long provinceId) {
        return ResponseEntity.ok(ApiResponse.success(locationSettingsService.getVenuesByProvince(provinceId)));
    }

    @PostMapping("/provinces/{provinceId}/venues")
    public ResponseEntity<ApiResponse<RaceVenueResponse>> createVenue(
            @PathVariable Long provinceId,
            @Valid @RequestBody RaceVenueRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Venue created",
                locationSettingsService.createVenue(provinceId, request)));
    }

    @PutMapping("/venues/{venueId}")
    public ResponseEntity<ApiResponse<RaceVenueResponse>> updateVenue(
            @PathVariable Long venueId,
            @Valid @RequestBody RaceVenueRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Venue updated",
                locationSettingsService.updateVenue(venueId, request)));
    }

    @DeleteMapping("/venues/{venueId}")
    public ResponseEntity<ApiResponse<Void>> deleteVenue(@PathVariable Long venueId) {
        locationSettingsService.deleteVenue(venueId);
        return ResponseEntity.ok(ApiResponse.success("Venue deleted", null));
    }

    @PutMapping("/venues/{venueId}/active")
    public ResponseEntity<ApiResponse<RaceVenueResponse>> updateVenueActive(
            @PathVariable Long venueId,
            @RequestParam boolean active) {
        return ResponseEntity.ok(ApiResponse.success("Venue active status updated",
                locationSettingsService.updateVenueActive(venueId, active)));
    }
}
