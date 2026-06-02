package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.TournamentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TournamentSummaryResponse {
    private Long id;
    private String name;
    private String description;
    private String location;
    private String locationKey;
    private String bannerUrl;
    private LocalDateTime registrationOpenAt;
    private LocalDateTime registrationCloseAt;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer minTeams;
    private Integer maxTeams;
    private TournamentStatus status;
    private LocalDateTime publishedAt;
    private LocalDateTime openedRegistrationAt;
}
