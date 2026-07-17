package com.minhthien.hoser_backend.exception;

import com.minhthien.hoser_backend.dto.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void featureDisabledUsesServiceUnavailableAndKeepsMessage() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleFeatureDisabled(
                new FeatureDisabledException("Betting feature is disabled"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Betting feature is disabled", response.getBody().getMessage());
    }
}
