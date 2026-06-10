package com.minhthien.hoser_backend.dto.kyc;

public record FptOcrResult(
        boolean passed,
        String idNumber,
        String fullName,
        String dateOfBirth,
        String gender,
        String address,
        String issueDate,
        String rawResponse,
        String rejectReason
) {
}
