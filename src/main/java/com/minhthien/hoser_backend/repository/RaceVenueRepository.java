package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.RaceVenue;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RaceVenueRepository extends JpaRepository<RaceVenue, Long> {
    @EntityGraph(attributePaths = {"province"})
    List<RaceVenue> findByProvinceIdOrderByNameAsc(Long provinceId);

    @EntityGraph(attributePaths = {"province"})
    List<RaceVenue> findByProvinceIdAndActiveTrueOrderByNameAsc(Long provinceId);

    boolean existsByProvinceId(Long provinceId);

    boolean existsByProvinceIdAndNameIgnoreCase(Long provinceId, String name);

    boolean existsByProvinceIdAndNameIgnoreCaseAndIdNot(Long provinceId, String name, Long id);
}
