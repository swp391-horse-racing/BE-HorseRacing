package com.minhthien.hoser_backend.service.kyc;

import com.minhthien.hoser_backend.entity.KycVerification;
import com.minhthien.hoser_backend.repository.KycVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KycFailurePersistenceService {
    private final KycVerificationRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public KycVerification save(KycVerification verification) {
        return repository.saveAndFlush(verification);
    }
}
