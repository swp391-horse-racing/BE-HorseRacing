package com.minhthien.hoser_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class KycHttpClientConfig {
    @Bean("kycRestTemplate")
    public RestTemplate kycRestTemplate(
            @Value("${kyc.http.connect-timeout-ms:10000}") int connectTimeoutMs,
            @Value("${kyc.http.read-timeout-ms:30000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }
}
