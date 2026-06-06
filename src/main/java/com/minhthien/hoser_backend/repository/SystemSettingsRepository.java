package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.SystemSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemSettingsRepository extends JpaRepository<SystemSettings, Long> {
}
