package com.minhthien.hoser_backend.service.kyc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minhthien.hoser_backend.config.VnptEkycProperties;
import com.minhthien.hoser_backend.exception.VnptEkycException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VnptEkycClientTest {
    @Mock
    private RestTemplate restTemplate;

    private VnptEkycClient client;

    @BeforeEach
    void setUp() {
        VnptEkycProperties properties = new VnptEkycProperties();
        properties.setBaseUrl("https://vnpt.test");
        properties.setTokenId("token-id");
        properties.setTokenKey("token-key");
        properties.setAccessToken("jwt-only");
        properties.setFaceMatchThreshold(new BigDecimal("80"));
        client = new VnptEkycClient(restTemplate, new ObjectMapper(), properties);
    }

    @Test
    void uploadFileReturnsHashForValidResponse() {
        respond("""
                {"message":"IDG-00000000","object":{"hash":"front-hash"}}
                """);

        assertEquals("front-hash", client.uploadFile(image("front"), "title", "description"));

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<HttpEntity> request = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq("https://vnpt.test/file-service/v1/addFile"),
                eq(HttpMethod.POST), request.capture(), eq(String.class));
        assertEquals("Bearer jwt-only", request.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals("token-id", request.getValue().getHeaders().getFirst("Token-id"));
        assertEquals("token-key", request.getValue().getHeaders().getFirst("Token-key"));
        assertTrue(request.getValue().getBody() instanceof MultiValueMap);
        @SuppressWarnings("unchecked")
        MultiValueMap<String, Object> body =
                (MultiValueMap<String, Object>) request.getValue().getBody();
        assertNotNull(body.getFirst("file"));
        assertEquals("title", body.getFirst("title"));
        assertEquals("description", body.getFirst("description"));
    }

    @Test
    void ocrMapsSuccessfulTwoSidedResponse() {
        respond(
                upload("front-hash"),
                upload("back-hash"),
                """
                {"message":"IDG-00000000","object":{
                  "id":"012345678901","name":"NGUYỄN VĂN A","birth_day":"01/01/2000",
                  "gender":"Nam","origin_location":"Hà Nội","recent_location":"TP HCM",
                  "issue_date":"01/01/2020","issue_place":"Cục CSQLHC",
                  "valid_date":"01/01/2040","card_type":"CĂN CƯỚC CÔNG DÂN",
                  "warning":[],"warning_msg":[],"tampering":{"is_legal":"yes"},
                  "id_fake_warning":"no","expire_warning":"no","back_expire_warning":"no"
                }}
                """);

        var result = client.callOcr(image("front"), image("back"));

        assertTrue(result.passed());
        assertEquals("front-hash", result.frontImageHash());
        assertEquals("012345678901", result.idNumber());
        assertEquals("NGUYỄN VĂN A", result.fullName());
        assertEquals("TP HCM", result.address());
    }

    @Test
    void ocrRejectsSuccessfulCodeWithoutObject() {
        respond(upload("front-hash"), upload("back-hash"),
                "{\"message\":\"IDG-00000000\"}");

        VnptEkycException error = assertThrows(VnptEkycException.class,
                () -> client.callOcr(image("front"), image("back")));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("Không đọc được thông tin từ CCCD.", error.getMessage());
    }

    @Test
    void unauthorizedResponseReturnsTokenMessage() {
        failWith(HttpStatus.UNAUTHORIZED);

        VnptEkycException error = assertThrows(VnptEkycException.class,
                () -> client.uploadFile(image("front"), "title", "description"));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatus());
        assertTrue(error.getMessage().contains("Access Token VNPT eKYC"));
    }

    @Test
    void rateLimitResponseReturnsQuotaMessage() {
        failWith(HttpStatus.TOO_MANY_REQUESTS);

        VnptEkycException error = assertThrows(VnptEkycException.class,
                () -> client.uploadFile(image("front"), "title", "description"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatus());
        assertEquals("Dịch vụ VNPT eKYC đã vượt hạn mức.", error.getMessage());
    }

    @Test
    void faceCompareAcceptsMatchAboveThreshold() {
        respond(upload("selfie-hash"), face("MATCH", "99.0"));

        var result = client.callFaceCompare("front-hash", image("selfie"));

        assertTrue(result.matched());
        assertEquals(new BigDecimal("99.0"), result.similarity());
    }

    @Test
    void faceCompareRejectsNoMatch() {
        respond(upload("selfie-hash"), face("NOMATCH", "99.0"));

        var result = client.callFaceCompare("front-hash", image("selfie"));

        assertFalse(result.matched());
        assertEquals("Khuôn mặt không khớp với ảnh trên CCCD.", result.rejectReason());
    }

    @Test
    void faceCompareRejectsProbabilityBelowThreshold() {
        respond(upload("selfie-hash"), face("MATCH", "79.99"));

        var result = client.callFaceCompare("front-hash", image("selfie"));

        assertFalse(result.matched());
        assertEquals(new BigDecimal("79.99"), result.similarity());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void respond(String... bodies) {
        ResponseEntity<String>[] responses = new ResponseEntity[bodies.length];
        for (int i = 0; i < bodies.length; i++) {
            responses[i] = ResponseEntity.ok(bodies[i]);
        }
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responses[0], java.util.Arrays.copyOfRange(responses, 1, responses.length));
    }

    private void failWith(HttpStatus status) {
        HttpClientErrorException exception = HttpClientErrorException.create(
                status, status.getReasonPhrase(), HttpHeaders.EMPTY,
                "{\"message\":\"IDG-ERROR\"}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(exception);
    }

    private String upload(String hash) {
        return "{\"message\":\"IDG-00000000\",\"object\":{\"hash\":\"" + hash + "\"}}";
    }

    private String face(String message, String probability) {
        return "{\"message\":\"IDG-00000000\",\"object\":{\"result\":\"ok\",\"msg\":\""
                + message + "\",\"prob\":" + probability + "}}";
    }

    private MockMultipartFile image(String name) {
        return new MockMultipartFile(name, name + ".jpg", "image/jpeg", new byte[]{1, 2, 3});
    }
}
