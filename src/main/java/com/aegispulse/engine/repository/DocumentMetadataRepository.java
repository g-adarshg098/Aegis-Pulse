package com.aegispulse.engine.repository;

import com.aegispulse.engine.entity.DocumentMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocumentMetadataRepository extends JpaRepository<DocumentMetadataEntity, Long> {
    Optional<DocumentMetadataEntity> findByDocumentId(String documentId);
    void deleteByDocumentId(String documentId);
}
