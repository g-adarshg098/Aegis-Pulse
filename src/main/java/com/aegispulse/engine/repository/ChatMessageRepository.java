package com.aegispulse.engine.repository;

import com.aegispulse.engine.entity.ChatMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {
    List<ChatMessageEntity> findBySessionIdOrderByCreatedAtAsc(String sessionId);
    List<ChatMessageEntity> findBySessionIdOrderByCreatedAtDesc(String sessionId, Pageable pageable);
    void deleteBySessionId(String sessionId);
}
