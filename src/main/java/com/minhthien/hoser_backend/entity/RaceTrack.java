package com.minhthien.hoser_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "race_tracks",
        indexes = {
                @Index(name = "idx_race_tracks_location_key", columnList = "location_key"),
                @Index(name = "idx_race_tracks_active", columnList = "active")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaceTrack {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "location_key", nullable = false, length = 50)
    private String locationKey;

    @Column(nullable = false, length = 160)
    private String locationName;

    @Column(length = 500)
    private String address;

    @Column(length = 80)
    private String trackType;

    @Column(length = 80)
    private String distance;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (active == null) {
            active = true;
        }
        locationKey = normalizeLocationKey(locationKey);
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
        locationKey = normalizeLocationKey(locationKey);
        if (active == null) {
            active = true;
        }
    }

    private String normalizeLocationKey(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }
}
