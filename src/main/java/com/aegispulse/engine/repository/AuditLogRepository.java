package com.aegispulse.engine.repository;

import com.aegispulse.engine.entity.AuditLogEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
    List<AuditLogEntity> findAllByOrderByTimestampDesc(Pageable pageable);
    List<AuditLogEntity> findBySessionIdOrderByTimestampDesc(String sessionId);
}
