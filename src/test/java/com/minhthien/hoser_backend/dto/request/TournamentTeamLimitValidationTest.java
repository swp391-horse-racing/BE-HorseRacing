package com.minhthien.hoser_backend.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TournamentTeamLimitValidationTest {
    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void createRequestRequiresAtLeastTwoTeams() {
        TournamentRequest request = new TournamentRequest();
        request.setMinTeams(1);

        assertEquals(Set.of("Minimum teams must be at least 2"), minTeamsMessages(request));

        request.setMinTeams(2);

        assertTrue(minTeamsMessages(request).isEmpty());
    }

    @Test
    void updateRequestRequiresAtLeastTwoTeams() {
        TournamentUpdateRequest request = new TournamentUpdateRequest();
        request.setMinTeams(1);

        assertEquals(Set.of("Minimum teams must be at least 2"), minTeamsMessages(request));

        request.setMinTeams(2);

        assertTrue(minTeamsMessages(request).isEmpty());
    }

    @Test
    void createRequestRequiresAtLeastOneHorsePerOwner() {
        TournamentRequest request = new TournamentRequest();
        request.setMinHorsesPerOwner(0);

        assertEquals(
                Set.of("Minimum horses per owner must be greater than zero"),
                minHorsesPerOwnerMessages(request));

        request.setMinHorsesPerOwner(1);

        assertTrue(minHorsesPerOwnerMessages(request).isEmpty());
    }

    @Test
    void updateRequestRequiresAtLeastOneHorsePerOwner() {
        TournamentUpdateRequest request = new TournamentUpdateRequest();
        request.setMinHorsesPerOwner(0);

        assertEquals(
                Set.of("Minimum horses per owner must be greater than zero"),
                minHorsesPerOwnerMessages(request));

        request.setMinHorsesPerOwner(1);

        assertTrue(minHorsesPerOwnerMessages(request).isEmpty());
    }

    private Set<String> minTeamsMessages(Object request) {
        return VALIDATOR.validate(request).stream()
                .filter(violation -> "minTeams".equals(violation.getPropertyPath().toString()))
                .map(violation -> violation.getMessage())
                .collect(Collectors.toSet());
    }

    private Set<String> minHorsesPerOwnerMessages(Object request) {
        return VALIDATOR.validate(request).stream()
                .filter(violation -> "minHorsesPerOwner".equals(violation.getPropertyPath().toString()))
                .map(violation -> violation.getMessage())
                .collect(Collectors.toSet());
    }
}
