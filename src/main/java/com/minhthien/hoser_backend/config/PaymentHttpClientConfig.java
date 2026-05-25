package com.minhthien.hoser_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

@Configuration
public class PaymentHttpClientConfig {

    @Bean
    public RestOperations paymentRestOperations() {
        return new RestTemplate();
    }
}
