package com.minhthien.hoser_backend.dto.kyc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VnptUploadResponse {
    private String message;
    private UploadObject object;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UploadObject {
        private String hash;
    }
}
