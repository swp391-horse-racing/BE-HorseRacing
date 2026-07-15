package com.minhthien.hoser_backend.dto.kyc;

import java.math.BigDecimal;

public record VnptFaceMatchResult(
        boolean matched,
        BigDecimal similarity,
        String rawResponse,
        String rejectReason
) {
}
