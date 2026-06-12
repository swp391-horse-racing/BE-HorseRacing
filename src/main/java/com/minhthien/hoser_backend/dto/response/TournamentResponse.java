package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.TournamentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TournamentResponse {
    private Long id;
    private String name;
    private String description;
    private String location;
    private String bannerUrl;
    private LocalDateTime registrationOpenAt;
    private LocalDateTime registrationCloseAt;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private LocalDateTime checkInDeadlineAt;
    private String rules;
    private Integer minTeams;
    private Integer maxTeams;
    private Integer minHorsesPerOwner;
    private Integer maxHorsesPerOwner;
    private TournamentStatus status;
    private LocalDateTime publishedAt;
    private LocalDateTime openedRegistrationAt;
    private Boolean jockeyChallengeEnabled;
    private Integer jockeyChallengeFirstPoints;
    private Integer jockeyChallengeSecondPoints;
    private Integer jockeyChallengeThirdPoints;
    private LocalDateTime jockeyChallengeFinalizedAt;
    private Long jockeyChallengeFinalizedBy;
    private LocalDateTime finalizedAt;
    private Long finalizedBy;
    private Integer pendingComplaintCountAtFinalize;
    private List<RaceResponse> races;
    private List<JockeyChallengePrizeResponse> jockeyChallengePrizes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
