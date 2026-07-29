package com.minhthien.hoser_backend.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaceParticipantLimitValidationTest {
    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void raceRequiresAtLeastTwoParticipants() {
        RaceRequest request = new RaceRequest();
        request.setMinParticipants(1);

        assertEquals(
                Set.of("Minimum participants must be at least 2"),
                minParticipantMessages(request));

        request.setMinParticipants(2);

        assertTrue(minParticipantMessages(request).isEmpty());
    }

    private Set<String> minParticipantMessages(RaceRequest request) {
        return VALIDATOR.validate(request).stream()
                .filter(violation -> "minParticipants".equals(violation.getPropertyPath().toString()))
                .map(violation -> violation.getMessage())
                .collect(Collectors.toSet());
    }
}
