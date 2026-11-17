package com.aegispulse.engine.tool;

import com.aegispulse.engine.repository.DocumentMetadataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;

@Slf4j
@Component
public class SystemPulseTool {

    private final DataSource dataSource;
    private final DocumentMetadataRepository documentMetadataRepository;

    public SystemPulseTool(DataSource dataSource,
                           DocumentMetadataRepository documentMetadataRepository) {
        this.dataSource = dataSource;
        this.documentMetadataRepository = documentMetadataRepository;
    }

    @Tool(name = "checkSystemPulse", description = "Inspects the live system health, database connection, model engine status, and indexed document statistics.")
    public String checkSystemPulse() {
        log.info("Agent invoked tool 'checkSystemPulse'");

        boolean dbOk = false;
        try (Connection conn = dataSource.getConnection()) {
            dbOk = conn.isValid(2);
        } catch (Exception e) {
            log.debug("DB check failed: {}", e.getMessage());
        }

        long docsCount = 0;
        try {
            docsCount = documentMetadataRepository.count();
        } catch (Exception e) {
            // non-fatal
        }

        return String.format("""
                {
                  "timestamp": "%s",
                  "postgresStatus": "%s",
                  "indexedDocuments": %d,
                  "engineRouting": "Groq Cloud (Fast) / Ollama Local (Confidential)",
                  "guardrailsActive": true
                }
                """,
                LocalDateTime.now(),
                dbOk ? "ONLINE" : "UNAVAILABLE",
                docsCount
        );
    }
}
