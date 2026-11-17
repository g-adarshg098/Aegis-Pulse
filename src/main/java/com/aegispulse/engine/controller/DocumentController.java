package com.aegispulse.engine.controller;

import com.aegispulse.engine.dto.DocumentSearchResponse;
import com.aegispulse.engine.dto.DocumentUploadResponse;
import com.aegispulse.engine.entity.DocumentMetadataEntity;
import com.aegispulse.engine.rag.DocumentIngestionService;
import com.aegispulse.engine.rag.RagSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
@CrossOrigin(origins = "*")
public class DocumentController {

    private final DocumentIngestionService ingestionService;
    private final RagSearchService searchService;

    public DocumentController(DocumentIngestionService ingestionService,
                              RagSearchService searchService) {
        this.ingestionService = ingestionService;
        this.searchService = searchService;
    }

    /**
     * Upload Document File (PDF, DOCX, TXT, MD)
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "tags", required = false) String tags) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Uploaded file cannot be empty."));
        }

        try {
            DocumentUploadResponse response = ingestionService.ingestFile(file, tags);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Failed to ingest file {}: {}", file.getOriginalFilename(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process document: " + e.getMessage()));
        }
    }

    /**
     * Ingest Raw Text / JSON Payload
     */
    @PostMapping("/text")
    public ResponseEntity<?> ingestRawText(@RequestBody Map<String, String> payload) {
        String content = payload.get("content");
        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Content field is required."));
        }

        String title = payload.getOrDefault("title", "text_document_" + System.currentTimeMillis());
        String tags = payload.get("tags");

        try {
            DocumentUploadResponse response = ingestionService.ingestText(title, content, tags);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Failed to ingest raw text: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process text: " + e.getMessage()));
        }
    }

    /**
     * List all ingested documents and their chunk counts
     */
    @GetMapping
    public ResponseEntity<List<DocumentMetadataEntity>> listDocuments() {
        return ResponseEntity.ok(ingestionService.listAllDocuments());
    }

    /**
     * Delete an ingested document by documentId
     */
    @DeleteMapping("/{documentId}")
    public ResponseEntity<?> deleteDocument(@PathVariable String documentId) {
        boolean deleted = ingestionService.deleteDocument(documentId);
        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "Document " + documentId + " deleted successfully."));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Document not found with ID: " + documentId));
        }
    }

    /**
     * Test semantic vector search across the knowledge base
     */
    @GetMapping("/search")
    public ResponseEntity<List<DocumentSearchResponse>> searchKnowledgeBase(
            @RequestParam String query,
            @RequestParam(defaultValue = "4") int topK,
            @RequestParam(defaultValue = "0.2") double minScore) {

        List<DocumentSearchResponse> results = searchService.search(query, topK, minScore);
        return ResponseEntity.ok(results);
    }
}
