package com.minhthien.hoser_backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "vnpt.ekyc")
public class VnptEkycProperties {
    private String baseUrl = "https://api.idg.vnpt.vn";
    private String tokenId;
    private String tokenKey;
    private String accessToken;
    private String macAddress = "TEST1";
    private int documentType = -1;
    private String cropParam = "0.14,0.3";
    private boolean validatePostcode = true;
    private BigDecimal faceMatchThreshold = new BigDecimal("80");
}
