package com.minhthien.hoser_backend.config;

import com.minhthien.hoser_backend.security.JwtAuthEntryPoint;
import com.minhthien.hoser_backend.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SecurityConfigCorsTest {
    private static final String ALLOWED_ORIGIN = "https://horseracing.id.vn";

    private CorsConfigurationSource configurationSource;

    @BeforeEach
    void setUp() {
        SecurityConfig securityConfig = new SecurityConfig(
                mock(UserDetailsService.class),
                mock(JwtAuthEntryPoint.class),
                mock(JwtAuthenticationFilter.class));
        ReflectionTestUtils.setField(securityConfig, "allowedOriginPatterns", List.of(
                "http://localhost:[*]",
                ALLOWED_ORIGIN));
        configurationSource = securityConfig.corsConfigurationSource();
    }

    @Test
    void allowedOriginReceivesCorsHeadersOnPreflight() throws Exception {
        MockHttpServletRequest request = preflight(ALLOWED_ORIGIN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        CorsConfiguration configuration = configurationSource.getCorsConfiguration(request);

        assertTrue(new DefaultCorsProcessor().processRequest(configuration, request, response));
        assertEquals(ALLOWED_ORIGIN, response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertEquals("true", response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        assertTrue(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS).contains("GET"));
        assertTrue(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS).contains("Authorization"));
    }

    @Test
    void unknownOriginIsRejected() throws Exception {
        MockHttpServletRequest request = preflight("https://evil.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CorsConfiguration configuration = configurationSource.getCorsConfiguration(request);

        assertFalse(new DefaultCorsProcessor().processRequest(configuration, request, response));
        assertEquals(403, response.getStatus());
        assertFalse(response.containsHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    private MockHttpServletRequest preflight(String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/users/me/bettable-races");
        request.addHeader(HttpHeaders.ORIGIN, origin);
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization");
        return request;
    }
}
