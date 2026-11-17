package com.aegispulse.engine.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "aegis_audit_logs", indexes = {
        @Index(name = "idx_audit_session_id", columnList = "sessionId"),
        @Index(name = "idx_audit_timestamp", columnList = "timestamp")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sessionId;

    @Column(columnDefinition = "TEXT")
    private String prompt;

    private String routedModel;

    private boolean confidential;

    private boolean fallbackOccurred;

    private long executionTimeMs;

    @Column(nullable = false)
    private String status; // SUCCESS, FAILED, BLOCKED

    @Column(columnDefinition = "TEXT")
    private String errorDetails;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
