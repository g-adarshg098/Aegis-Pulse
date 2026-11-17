package com.aegispulse.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemStatusResponse {
    private String status;
    private String activePrimaryModel;
    private String activeLocalModel;
    private boolean groqReachable;
    private boolean ollamaReachable;
    private boolean postgresReachable;
    private boolean redisReachable;
    private String vectorStoreType;
    private long indexedDocumentCount;
    private long totalAuditRecords;
    private Map<String, Object> details;
}
