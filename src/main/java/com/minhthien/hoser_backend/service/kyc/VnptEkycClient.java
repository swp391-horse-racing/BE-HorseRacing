package com.minhthien.hoser_backend.service.kyc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minhthien.hoser_backend.config.VnptEkycProperties;
import com.minhthien.hoser_backend.dto.kyc.VnptFaceCompareResponse;
import com.minhthien.hoser_backend.dto.kyc.VnptFaceMatchResult;
import com.minhthien.hoser_backend.dto.kyc.VnptOcrResponse;
import com.minhthien.hoser_backend.dto.kyc.VnptOcrResult;
import com.minhthien.hoser_backend.dto.kyc.VnptUploadResponse;
import com.minhthien.hoser_backend.exception.VnptEkycException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class VnptEkycClient {
    static final String SUCCESS_CODE = "IDG-00000000";
    static final String UPLOAD_PATH = "/file-service/v1/addFile";
    static final String OCR_PATH = "/ai/v1/ocr/id";
    static final String FACE_COMPARE_PATH = "/ai/v1/face/compare";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final VnptEkycProperties properties;

    public VnptEkycClient(@Qualifier("kycRestTemplate") RestTemplate restTemplate,
                          ObjectMapper objectMapper,
                          VnptEkycProperties properties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public String uploadFile(MultipartFile file, String title, String description) {
        String transactionId = requestToken();
        validateCredentials();
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", resource(file));
            body.add("title", title);
            body.add("description", description);

            String raw = exchange(UPLOAD_PATH, new HttpEntity<>(body, multipartHeaders()), transactionId,
                    "Ảnh xác minh không hợp lệ hoặc quá mờ.");
            VnptUploadResponse response = objectMapper.readValue(raw, VnptUploadResponse.class);
            String hash = response.getObject() == null ? null : response.getObject().getHash();
            if (!SUCCESS_CODE.equals(response.getMessage()) || !hasText(hash)) {
                logProviderResult(UPLOAD_PATH, 200, response.getMessage(), transactionId);
                throw new VnptEkycException(HttpStatus.BAD_REQUEST,
                        "Ảnh xác minh không hợp lệ hoặc quá mờ.");
            }
            logProviderResult(UPLOAD_PATH, 200, response.getMessage(), transactionId);
            return hash.trim();
        } catch (JsonProcessingException ex) {
            logProviderResult(UPLOAD_PATH, 200, "INVALID_RESPONSE", transactionId);
            throw new VnptEkycException(HttpStatus.BAD_GATEWAY,
                    "VNPT eKYC trả về dữ liệu không hợp lệ. Vui lòng thử lại.");
        } catch (IOException ex) {
            logProviderResult(UPLOAD_PATH, 0, "FILE_READ_ERROR", transactionId);
            throw new VnptEkycException(HttpStatus.BAD_REQUEST,
                    "Không thể đọc ảnh xác minh.");
        }
    }

    public VnptOcrResult callOcr(MultipartFile frontImage, MultipartFile backImage) {
        String frontHash = uploadFile(frontImage, "CCCD mặt trước", "Ảnh mặt trước CCCD dùng cho KYC");
        String backHash = uploadFile(backImage, "CCCD mặt sau", "Ảnh mặt sau CCCD dùng cho KYC");
        String clientSession = clientSession();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("img_front", frontHash);
        body.put("img_back", backHash);
        body.put("client_session", clientSession);
        body.put("type", properties.getDocumentType());
        body.put("crop_param", properties.getCropParam());
        body.put("validate_postcode", properties.isValidatePostcode());
        body.put("token", requestToken());

        String raw = exchange(OCR_PATH, new HttpEntity<>(body, jsonHeaders()), clientSession,
                "Ảnh CCCD không hợp lệ hoặc quá mờ.");
        try {
            VnptOcrResponse response = objectMapper.readValue(raw, VnptOcrResponse.class);
            VnptOcrResponse.OcrObject data = response.getObject();
            if (!SUCCESS_CODE.equals(response.getMessage()) || data == null) {
                logProviderResult(OCR_PATH, 200, response.getMessage(), clientSession);
                throw new VnptEkycException(HttpStatus.BAD_REQUEST,
                        "Không đọc được thông tin từ CCCD.");
            }
            boolean passed = hasText(data.getId()) && hasText(data.getName());
            logProviderResult(OCR_PATH, 200, response.getMessage(), clientSession);
            return new VnptOcrResult(
                    passed,
                    frontHash,
                    clean(data.getId()),
                    clean(data.getName()),
                    clean(data.getBirthDay()),
                    clean(data.getGender()),
                    firstText(data.getRecentLocation(), data.getOriginLocation()),
                    clean(data.getIssueDate()),
                    raw,
                    passed ? null : "Không đọc được thông tin từ CCCD.");
        } catch (JsonProcessingException ex) {
            logProviderResult(OCR_PATH, 200, "INVALID_RESPONSE", clientSession);
            throw new VnptEkycException(HttpStatus.BAD_GATEWAY,
                    "VNPT eKYC trả về dữ liệu không hợp lệ. Vui lòng thử lại.");
        }
    }

    public VnptFaceMatchResult callFaceCompare(String frontImageHash, MultipartFile selfie) {
        String selfieHash = uploadFile(selfie, "Ảnh selfie", "Ảnh khuôn mặt dùng để đối chiếu KYC");
        String clientSession = clientSession();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("img_front", frontImageHash);
        body.put("img_face", selfieHash);
        body.put("client_session", clientSession);
        body.put("token", requestToken());

        String raw = exchange(FACE_COMPARE_PATH, new HttpEntity<>(body, jsonHeaders()), clientSession,
                "Ảnh khuôn mặt không hợp lệ hoặc quá mờ.");
        try {
            VnptFaceCompareResponse response = objectMapper.readValue(raw, VnptFaceCompareResponse.class);
            VnptFaceCompareResponse.FaceCompareObject data = response.getObject();
            if (!SUCCESS_CODE.equals(response.getMessage()) || data == null) {
                logProviderResult(FACE_COMPARE_PATH, 200, response.getMessage(), clientSession);
                throw new VnptEkycException(HttpStatus.BAD_REQUEST,
                        "Không thể đối chiếu khuôn mặt với VNPT eKYC.");
            }
            BigDecimal probability = data.getProb();
            boolean providerMatched = "MATCH".equalsIgnoreCase(clean(data.getMsg()));
            boolean thresholdPassed = probability != null
                    && probability.compareTo(properties.getFaceMatchThreshold()) >= 0;
            boolean matched = providerMatched && thresholdPassed;
            String reason = matched ? null : "Khuôn mặt không khớp với ảnh trên CCCD.";
            logProviderResult(FACE_COMPARE_PATH, 200, response.getMessage(), clientSession);
            return new VnptFaceMatchResult(matched, probability, raw, reason);
        } catch (JsonProcessingException ex) {
            logProviderResult(FACE_COMPARE_PATH, 200, "INVALID_RESPONSE", clientSession);
            throw new VnptEkycException(HttpStatus.BAD_GATEWAY,
                    "VNPT eKYC trả về dữ liệu không hợp lệ. Vui lòng thử lại.");
        }
    }

    private String exchange(String path, HttpEntity<?> request, String transactionId, String badRequestMessage) {
        validateCredentials();
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint(path), HttpMethod.POST, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                logProviderResult(path, response.getStatusCode().value(),
                        extractProviderMessage(response.getBody()), transactionId);
                throw providerError(response.getStatusCode().value(), badRequestMessage);
            }
            return response.getBody() == null ? "" : response.getBody();
        } catch (HttpStatusCodeException ex) {
            String providerMessage = extractProviderMessage(ex.getResponseBodyAsString());
            logProviderResult(path, ex.getStatusCode().value(), providerMessage, transactionId);
            throw providerError(ex.getStatusCode().value(), badRequestMessage);
        } catch (RestClientException ex) {
            logProviderResult(path, 0, "NETWORK_ERROR", transactionId);
            throw new VnptEkycException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Không thể kết nối VNPT eKYC. Vui lòng thử lại.");
        }
    }

    private VnptEkycException providerError(int status, String badRequestMessage) {
        if (status == 400) {
            return new VnptEkycException(HttpStatus.BAD_REQUEST, badRequestMessage);
        }
        if (status == 401 || status == 403) {
            return new VnptEkycException(HttpStatus.valueOf(status),
                    "Access Token VNPT eKYC đã hết hạn. Vui lòng cập nhật token.");
        }
        if (status == 429) {
            return new VnptEkycException(HttpStatus.TOO_MANY_REQUESTS,
                    "Dịch vụ VNPT eKYC đã vượt hạn mức.");
        }
        if (status >= 500) {
            return new VnptEkycException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Dịch vụ VNPT eKYC tạm thời gặp sự cố. Vui lòng thử lại.");
        }
        return new VnptEkycException(HttpStatus.BAD_GATEWAY,
                "VNPT eKYC trả về phản hồi không hợp lệ. Vui lòng thử lại.");
    }

    private HttpHeaders multipartHeaders() {
        HttpHeaders headers = authenticationHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return headers;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = authenticationHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("mac-address", properties.getMacAddress());
        return headers;
    }

    private HttpHeaders authenticationHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(properties.getAccessToken().trim());
        headers.set("Token-id", properties.getTokenId().trim());
        headers.set("Token-key", properties.getTokenKey().trim());
        return headers;
    }

    private void validateCredentials() {
        if (!hasText(properties.getTokenId()) || !hasText(properties.getTokenKey())
                || !hasText(properties.getAccessToken())) {
            throw new VnptEkycException(HttpStatus.SERVICE_UNAVAILABLE,
                    "VNPT eKYC chưa được cấu hình trên máy chủ.");
        }
        if (properties.getAccessToken().trim().toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            throw new VnptEkycException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Access Token VNPT eKYC trên máy chủ không đúng định dạng.");
        }
    }

    private ByteArrayResource resource(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        String filename = hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "image.jpg";
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    private String endpoint(String path) {
        String baseUrl = properties.getBaseUrl().trim();
        return (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl) + path;
    }

    private String clientSession() {
        return "WEB_HorseRacing_Linux_Server_1.0_" + UUID.randomUUID() + "_" + Instant.now().toEpochMilli();
    }

    private String requestToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String extractProviderMessage(String raw) {
        if (!hasText(raw)) return "EMPTY_RESPONSE";
        try {
            JsonNode root = objectMapper.readTree(raw);
            return sanitize(root.path("message").asText("UNKNOWN"));
        } catch (JsonProcessingException ex) {
            return "UNPARSEABLE_RESPONSE";
        }
    }

    private void logProviderResult(String path, int status, String providerMessage, String transactionId) {
        log.info("VNPT eKYC endpoint={}, httpStatus={}, vnptMessage={}, transactionId={}",
                path, status, sanitize(providerMessage), sanitize(transactionId));
    }

    private String sanitize(String value) {
        if (value == null) return "UNKNOWN";
        String sanitized = value.replaceAll("[\\r\\n\\t]", " ").replaceAll("[^\\p{L}\\p{N}_.: -]", "_");
        return sanitized.substring(0, Math.min(sanitized.length(), 160));
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) return value.trim();
        }
        return null;
    }

    private String clean(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
