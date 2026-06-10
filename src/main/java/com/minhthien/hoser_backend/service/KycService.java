package com.minhthien.hoser_backend.service;

import com.minhthien.hoser_backend.dto.response.KycFaceMatchResponse;
import com.minhthien.hoser_backend.dto.response.KycOcrResponse;
import com.minhthien.hoser_backend.enums.UserRole;
import org.springframework.web.multipart.MultipartFile;

public interface KycService {
    KycOcrResponse verifyCccd(Long userId, UserRole requestedRole,
                              MultipartFile cccdFront, MultipartFile cccdBack);

    KycFaceMatchResponse verifyFace(Long userId, Long verificationId, MultipartFile selfie);
}
