package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.EmailEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailEventLogRepository extends JpaRepository<EmailEventLog, Long> {
}
