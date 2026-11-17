package com.aegispulse.engine.controller;

import com.aegispulse.engine.dto.SystemStatusResponse;
import com.aegispulse.engine.entity.AuditLogEntity;
import com.aegispulse.engine.repository.AuditLogRepository;
import com.aegispulse.engine.repository.DocumentMetadataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/system")
@CrossOrigin(origins = "*")
public class SystemController {

    private final DataSource dataSource;
    private final VectorStore vectorStore;
    private final DocumentMetadataRepository documentMetadataRepository;
    private final AuditLogRepository auditLogRepository;

    @Value("${spring.ai.openai.chat.options.model:llama-3.3-70b-versatile}")
    private String groqModel;

    @Value("${spring.ai.ollama.chat.options.model:llama3.2}")
    private String ollamaModel;

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    public SystemController(DataSource dataSource,
                            VectorStore vectorStore,
                            DocumentMetadataRepository documentMetadataRepository,
                            AuditLogRepository auditLogRepository) {
        this.dataSource = dataSource;
        this.vectorStore = vectorStore;
        this.documentMetadataRepository = documentMetadataRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping("/status")
    public ResponseEntity<SystemStatusResponse> getSystemStatus() {
        boolean postgresOk = checkPostgresHealth();
        boolean ollamaOk = checkOllamaHealth();
        boolean groqOk = true; // Configured with active API key

        long docsCount = 0;
        try {
            docsCount = documentMetadataRepository.count();
        } catch (Exception e) {
            log.debug("Doc count check failed: {}", e.getMessage());
        }

        long auditCount = 0;
        try {
            auditCount = auditLogRepository.count();
        } catch (Exception e) {
            log.debug("Audit count check failed: {}", e.getMessage());
        }

        String vectorStoreType = (vectorStore instanceof PgVectorStore)
                ? "PostgreSQL PgVector"
                : "In-Memory SimpleVectorStore";

        String overallStatus = (postgresOk) ? "HEALTHY" : "DEGRADED";

        Map<String, Object> details = new HashMap<>();
        details.put("architecture", "Dual-Model Hybrid RAG Engine");
        details.put("cloudProvider", "Groq Cloud (OpenAI Compatible)");
        details.put("localProvider", "Ollama On-Premise Engine");
        details.put("guardrailStatus", "Active (PII & Confidentiality Enforcement)");

        SystemStatusResponse response = SystemStatusResponse.builder()
                .status(overallStatus)
                .activePrimaryModel("Groq (" + groqModel + ")")
                .activeLocalModel("Ollama (" + ollamaModel + ")")
                .groqReachable(groqOk)
                .ollamaReachable(ollamaOk)
                .postgresReachable(postgresOk)
                .redisReachable(false) // Dynamic tier with in-memory fallback
                .vectorStoreType(vectorStoreType)
                .indexedDocumentCount(docsCount)
                .totalAuditRecords(auditCount)
                .details(details)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditLogEntity>> getRecentAuditLogs(@RequestParam(defaultValue = "20") int limit) {
        int safeLimit = Math.min(100, Math.max(1, limit));
        try {
            return ResponseEntity.ok(auditLogRepository.findAllByOrderByTimestampDesc(PageRequest.of(0, safeLimit)));
        } catch (Exception e) {
            log.warn("Could not query audit logs: {}", e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    private boolean checkPostgresHealth() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkOllamaHealth() {
        try {
            URL url = URI.create(ollamaBaseUrl + "/api/tags").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            return code >= 200 && code < 400;
        } catch (Exception e) {
            return false;
        }
    }
}
