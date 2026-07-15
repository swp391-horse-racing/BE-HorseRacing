package com.minhthien.hoser_backend.dto.kyc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VnptOcrResponse {
    private String message;
    private OcrObject object;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OcrObject {
        private String id;
        private String name;

        @JsonProperty("birth_day")
        private String birthDay;

        private String gender;
        private String nationality;

        @JsonProperty("origin_location")
        private String originLocation;

        @JsonProperty("recent_location")
        private String recentLocation;

        @JsonProperty("issue_date")
        private String issueDate;

        @JsonProperty("issue_place")
        private String issuePlace;

        @JsonProperty("valid_date")
        private String validDate;

        @JsonProperty("card_type")
        private String cardType;

        private JsonNode warning;

        @JsonProperty("warning_msg")
        private JsonNode warningMessage;

        private Tampering tampering;

        @JsonProperty("id_fake_warning")
        private JsonNode idFakeWarning;

        @JsonProperty("expire_warning")
        private JsonNode expireWarning;

        @JsonProperty("back_expire_warning")
        private JsonNode backExpireWarning;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Tampering {
        @JsonProperty("is_legal")
        private JsonNode legal;
    }
}
