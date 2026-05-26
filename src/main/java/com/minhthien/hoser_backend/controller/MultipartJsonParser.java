package com.minhthien.hoser_backend.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minhthien.hoser_backend.exception.BadRequestException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class MultipartJsonParser {
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public <T> T parse(String data, Class<T> targetType) {
        if (data == null || data.isBlank()) {
            throw new BadRequestException("Multipart data is required");
        }
        T request;
        try {
            request = objectMapper.readValue(data, targetType);
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("Invalid multipart JSON data");
        }
        validate(request);
        return request;
    }

    private <T> void validate(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (violations.isEmpty()) {
            return;
        }
        String message = violations.stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining("; "));
        throw new BadRequestException(message);
    }
}
