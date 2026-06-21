package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.RefereeSalaryConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RefereeSalaryConfigRepository extends JpaRepository<RefereeSalaryConfig, Long> {
    List<RefereeSalaryConfig> findAllByOrderByCreatedAtDesc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
