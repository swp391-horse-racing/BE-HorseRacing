package com.minhthien.hoser_backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class TournamentRequest {
    @NotBlank(message = "Tournament name is required")
    @Size(max = 160, message = "Tournament name must be at most 160 characters")
    private String name;

    @Size(max = 2000, message = "Description must be at most 2000 characters")
    private String description;

    @NotBlank(message = "Location is required")
    @Size(max = 255, message = "Location must be at most 255 characters")
    private String location;

    @NotNull(message = "Province is required")
    private Long provinceId;

    @Size(max = 500, message = "Banner URL must be at most 500 characters")
    private String bannerUrl;

    @NotNull(message = "Registration open time is required")
    private LocalDateTime registrationOpenAt;

    @NotNull(message = "Registration close time is required")
    private LocalDateTime registrationCloseAt;

    @NotNull(message = "Tournament start time is required")
    private LocalDateTime startAt;

    @NotNull(message = "Tournament end time is required")
    private LocalDateTime endAt;

    private LocalDateTime checkInDeadlineAt;

    @Size(max = 10000, message = "Tournament rules must not exceed 10000 characters")
    private String rules;

    @NotNull(message = "Minimum teams is required")
    @Positive(message = "Minimum teams must be greater than zero")
    private Integer minTeams;

    @NotNull(message = "Maximum teams is required")
    @Positive(message = "Maximum teams must be greater than zero")
    private Integer maxTeams;

    @NotNull(message = "Minimum horses per owner is required")
    @Positive(message = "Minimum horses per owner must be greater than zero")
    private Integer minHorsesPerOwner;

    @NotNull(message = "Maximum horses per owner is required")
    @Positive(message = "Maximum horses per owner must be greater than zero")
    private Integer maxHorsesPerOwner;

    @Schema(description = "Enable daily jockey challenge scoring for this race day", example = "true")
    private Boolean jockeyChallengeEnabled = false;

    @Positive(message = "First place points must be greater than zero")
    @Schema(description = "Challenge points awarded to a jockey for rank 1 in each race", example = "3", defaultValue = "3")
    private Integer jockeyChallengeFirstPoints = 3;

    @Positive(message = "Second place points must be greater than zero")
    @Schema(description = "Challenge points awarded to a jockey for rank 2 in each race", example = "2", defaultValue = "2")
    private Integer jockeyChallengeSecondPoints = 2;

    @Positive(message = "Third place points must be greater than zero")
    @Schema(description = "Challenge points awarded to a jockey for rank 3 in each race", example = "1", defaultValue = "1")
    private Integer jockeyChallengeThirdPoints = 1;

    @Valid
    @Schema(description = "Daily challenge prize config. Use [] when there is no challenge prize money.")
    private List<JockeyChallengePrizeRequest> jockeyChallengePrizes = new ArrayList<>();
}
