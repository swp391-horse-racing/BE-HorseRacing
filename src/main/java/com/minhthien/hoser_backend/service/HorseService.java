package com.minhthien.hoser_backend.service;

import com.minhthien.hoser_backend.dto.request.AdminReviewRequest;
import com.minhthien.hoser_backend.dto.request.HorseRequest;
import com.minhthien.hoser_backend.dto.request.HorseUpdateRequest;
import com.minhthien.hoser_backend.dto.response.HorseResponse;
import com.minhthien.hoser_backend.enums.HorseStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface HorseService {
    HorseResponse createHorse(Long ownerId, HorseRequest request, MultipartFile image, MultipartFile document);

    List<HorseResponse> getApprovedHorses();

    List<HorseResponse> getOwnerHorses(Long ownerId);

    HorseResponse getOwnerHorse(Long ownerId, Long horseId);

    HorseResponse getHorseForViewer(Long viewerId, Long horseId);

    HorseResponse updateHorse(Long ownerId, Long horseId, HorseUpdateRequest request, MultipartFile image, MultipartFile document);

    void deleteHorse(Long ownerId, Long horseId);

    List<HorseResponse> getAdminHorses(HorseStatus status);

    HorseResponse approveHorse(Long horseId, Long adminId);

    HorseResponse rejectHorse(Long horseId, Long adminId, AdminReviewRequest request);

    HorseResponse suspendHorse(Long horseId, Long adminId, AdminReviewRequest request);
}
