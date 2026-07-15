package com.minhthien.hoser_backend.dto.kyc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VnptFaceCompareResponse {
    private String message;
    private FaceCompareObject object;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FaceCompareObject {
        private String result;
        private String msg;
        private BigDecimal prob;
    }
}
