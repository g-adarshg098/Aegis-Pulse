package com.aegispulse.engine.rag;

import com.aegispulse.engine.dto.ChatResponse.Citation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RagSearchServiceTest {

    private VectorStore vectorStore;
    private RagSearchService searchService;

    @BeforeEach
    void setUp() {
        vectorStore = Mockito.mock(VectorStore.class);
        searchService = new RagSearchService(vectorStore);
    }

    @Test
    void testExtractCitations() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("filename", "aegis-architecture.pdf");
        meta.put("chunkIndex", 2);

        Document doc = new Document("Aegis Pulse combines cloud inference with local air-gapped models.", meta);

        List<Citation> citations = searchService.extractCitations(List.of(doc));
        assertEquals(1, citations.size());
        assertEquals("aegis-architecture.pdf", citations.get(0).getSource());
        assertEquals(2, citations.get(0).getChunkIndex());
        assertTrue(citations.get(0).getSnippet().contains("Aegis Pulse"));
    }

    @Test
    void testFormatRagContext() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("filename", "security-guide.md");

        Document doc = new Document("Always enforce TLS 1.3 and secret masking.", meta);
        String formatted = searchService.formatRagContext(List.of(doc));

        assertTrue(formatted.contains("RELEVANT ENTERPRISE KNOWLEDGE CONTEXT"));
        assertTrue(formatted.contains("Source: security-guide.md"));
        assertTrue(formatted.contains("Always enforce TLS 1.3"));
    }
}
