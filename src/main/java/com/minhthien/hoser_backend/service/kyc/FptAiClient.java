package com.minhthien.hoser_backend.service.kyc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minhthien.hoser_backend.dto.kyc.FptFaceMatchResult;
import com.minhthien.hoser_backend.dto.kyc.FptOcrResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;

@Component
public class FptAiClient {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public FptAiClient(@Qualifier("kycRestTemplate") RestTemplate restTemplate,
                       ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Value("${fpt.ai.api-key}")
    private String apiKey;
    @Value("${fpt.ai.ocr-url:https://api.fpt.ai/vision/idr/vnm}")
    private String ocrUrl;
    @Value("${fpt.ai.face-match-url:https://api.fpt.ai/dmp/checkface/v1}")
    private String faceMatchUrl;
    @Value("${kyc.face-match-threshold:80}")
    private BigDecimal faceMatchThreshold;

    public FptOcrResult callOcr(MultipartFile image) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", resource(image.getBytes(), image.getOriginalFilename()));
            String raw = postMultipart(ocrUrl, body);
            JsonNode root = objectMapper.readTree(raw);
            JsonNode data = root.path("data").path(0);
            boolean passed = root.path("errorCode").asInt(-1) == 0
                    && hasText(data.path("id").asText())
                    && hasText(data.path("name").asText());
            return new FptOcrResult(
                    passed,
                    text(data, "id"),
                    text(data, "name"),
                    firstText(data, "dob", "date_of_birth"),
                    text(data, "sex"),
                    firstText(data, "address", "home"),
                    firstText(data, "doe", "issue_date"),
                    raw,
                    passed ? null : "Không đọc được CCCD mặt trước");
        } catch (IOException | RestClientException ex) {
            return new FptOcrResult(false, null, null, null, null, null, null,
                    null, "Không thể xác minh CCCD với FPT.AI");
        }
    }

    public FptFaceMatchResult callFaceMatch(byte[] cccdFront, String frontFilename, MultipartFile selfie) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file[]", resource(cccdFront, frontFilename));
            body.add("file[]", resource(selfie.getBytes(), selfie.getOriginalFilename()));
            String raw = postMultipart(faceMatchUrl, body);
            JsonNode root = objectMapper.readTree(raw);
            JsonNode data = root.path("data");
            BigDecimal similarity = decimal(data, "similarity");
            boolean providerMatched = data.path("isMatch").asBoolean(false);
            boolean codeOk = "200".equals(root.path("code").asText());
            boolean passed = codeOk && providerMatched
                    && similarity != null && similarity.compareTo(faceMatchThreshold) >= 0;
            String reason = null;
            if (!codeOk || !providerMatched) {
                reason = "Khuôn mặt không khớp với CCCD";
            } else if (similarity == null || similarity.compareTo(faceMatchThreshold) < 0) {
                reason = "Điểm khớp khuôn mặt quá thấp";
            }
            return new FptFaceMatchResult(passed, similarity, raw, reason);
        } catch (IOException | RestClientException ex) {
            return new FptFaceMatchResult(false, null, null, "Không thể xác minh khuôn mặt với FPT.AI");
        }
    }

    public byte[] download(String url) {
        try {
            byte[] bytes = restTemplate.getForObject(url, byte[].class);
            if (bytes == null || bytes.length == 0) {
                throw new RestClientException("Empty image");
            }
            return bytes;
        } catch (RestClientException ex) {
            throw new IllegalStateException("Không thể tải lại ảnh CCCD đã lưu", ex);
        }
    }

    private String postMultipart(String url, MultiValueMap<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("api-key", apiKey);
        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        return response.getBody() == null ? "" : response.getBody();
    }

    private ByteArrayResource resource(byte[] bytes, String filename) {
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return hasText(filename) ? filename : "image.jpg";
            }
        };
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return hasText(value) ? value.trim() : null;
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null) return value;
        }
        return null;
    }

    private BigDecimal decimal(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        try {
            return hasText(value) ? new BigDecimal(value) : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
