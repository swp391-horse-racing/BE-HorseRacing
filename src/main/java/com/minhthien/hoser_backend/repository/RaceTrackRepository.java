package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.RaceTrack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RaceTrackRepository extends JpaRepository<RaceTrack, Long> {
    List<RaceTrack> findByLocationKeyOrderByNameAsc(String locationKey);

    List<RaceTrack> findByLocationKeyAndActiveOrderByNameAsc(String locationKey, Boolean active);

    List<RaceTrack> findByActiveOrderByLocationKeyAscNameAsc(Boolean active);

    List<RaceTrack> findAllByOrderByLocationKeyAscNameAsc();
}
