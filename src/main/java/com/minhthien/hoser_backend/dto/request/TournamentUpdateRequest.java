package com.minhthien.hoser_backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TournamentUpdateRequest {
    @Size(max = 160, message = "Tournament name must be at most 160 characters")
    private String name;

    @Size(max = 2000, message = "Description must be at most 2000 characters")
    private String description;

    @Size(max = 255, message = "Location must be at most 255 characters")
    private String location;

    private Long provinceId;

    @Size(max = 500, message = "Banner URL must be at most 500 characters")
    private String bannerUrl;

    private LocalDateTime registrationOpenAt;

    private LocalDateTime registrationCloseAt;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    private LocalDateTime checkInDeadlineAt;

    @Size(max = 10000, message = "Tournament rules must not exceed 10000 characters")
    private String rules;

    @Min(value = 2, message = "Minimum teams must be at least 2")
    private Integer minTeams;

    @Positive(message = "Maximum teams must be greater than zero")
    private Integer maxTeams;

    @Positive(message = "Minimum horses per owner must be greater than zero")
    private Integer minHorsesPerOwner;

    @Positive(message = "Maximum horses per owner must be greater than zero")
    private Integer maxHorsesPerOwner;

    private Boolean jockeyChallengeEnabled;

    @Positive(message = "First place points must be greater than zero")
    private Integer jockeyChallengeFirstPoints;

    @Positive(message = "Second place points must be greater than zero")
    private Integer jockeyChallengeSecondPoints;

    @Positive(message = "Third place points must be greater than zero")
    private Integer jockeyChallengeThirdPoints;

    @Valid
    private List<JockeyChallengePrizeRequest> jockeyChallengePrizes;
}
