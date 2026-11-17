package com.aegispulse.engine.rag;

import com.aegispulse.engine.dto.DocumentUploadResponse;
import com.aegispulse.engine.entity.DocumentMetadataEntity;
import com.aegispulse.engine.repository.DocumentMetadataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class DocumentIngestionService {

    private final VectorStore vectorStore;
    private final DocumentMetadataRepository documentMetadataRepository;
    private final TokenTextSplitter tokenTextSplitter;

    public DocumentIngestionService(VectorStore vectorStore,
                                    DocumentMetadataRepository documentMetadataRepository) {
        this.vectorStore = vectorStore;
        this.documentMetadataRepository = documentMetadataRepository;
        // 500 default chunk size, 80 tokens overlap
        this.tokenTextSplitter = new TokenTextSplitter(500, 80, 5, 2000, true);
    }

    public DocumentUploadResponse ingestFile(MultipartFile file, String tags) throws IOException {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.txt";
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        byte[] bytes = file.getBytes();

        List<Document> rawDocuments;
        String lowerFilename = filename.toLowerCase();

        if (lowerFilename.endsWith(".txt") || lowerFilename.endsWith(".md") || lowerFilename.endsWith(".json")) {
            String text = new String(bytes, StandardCharsets.UTF_8);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", filename);
            metadata.put("fileType", contentType);
            rawDocuments = List.of(new Document(text, metadata));
        } else {
            // Use Apache Tika Reader for PDF, Word (.docx), PPTX, etc.
            ByteArrayResource resource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
            TikaDocumentReader tikaReader = new TikaDocumentReader(resource);
            rawDocuments = tikaReader.get();
        }

        return processAndStore(rawDocuments, filename, contentType, bytes.length, tags);
    }

    public DocumentUploadResponse ingestText(String title, String content, String tags) {
        String filename = (title != null && !title.isBlank()) ? title : "raw_text_entry";
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", filename);
        metadata.put("fileType", "text/plain");

        List<Document> rawDocuments = List.of(new Document(content, metadata));
        return processAndStore(rawDocuments, filename, "text/plain", content.getBytes(StandardCharsets.UTF_8).length, tags);
    }

    private DocumentUploadResponse processAndStore(List<Document> rawDocuments,
                                                   String filename,
                                                   String contentType,
                                                   long fileSize,
                                                   String tags) {
        String documentId = UUID.randomUUID().toString();
        List<Document> splitDocuments = tokenTextSplitter.apply(rawDocuments);

        int chunkIndex = 0;
        int totalChunks = splitDocuments.size();

        for (Document doc : splitDocuments) {
            doc.getMetadata().put("documentId", documentId);
            doc.getMetadata().put("filename", filename);
            doc.getMetadata().put("chunkIndex", chunkIndex++);
            doc.getMetadata().put("totalChunks", totalChunks);
            doc.getMetadata().put("tags", tags != null ? tags : "");
            doc.getMetadata().put("ingestedAt", LocalDateTime.now().toString());
        }

        // Store vectors in pgvector / simple vector store
        try {
            vectorStore.add(splitDocuments);
            log.info("Indexed {} chunks for document '{}' ({})", totalChunks, filename, documentId);
        } catch (Exception e) {
            log.error("Failed to add vectors to vector store: {}", e.getMessage(), e);
            throw new RuntimeException("Vector Store indexing error: " + e.getMessage(), e);
        }

        // Persist metadata
        DocumentMetadataEntity meta = DocumentMetadataEntity.builder()
                .documentId(documentId)
                .filename(filename)
                .fileType(contentType)
                .totalChunks(totalChunks)
                .fileSize(fileSize)
                .tags(tags)
                .uploadedAt(LocalDateTime.now())
                .build();

        try {
            documentMetadataRepository.save(meta);
        } catch (Exception e) {
            log.warn("Could not save document metadata to database: {}", e.getMessage());
        }

        return DocumentUploadResponse.builder()
                .documentId(documentId)
                .filename(filename)
                .fileType(contentType)
                .chunksCount(totalChunks)
                .status("SUCCESS")
                .message("Document successfully chunked, embedded, and stored.")
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Transactional
    public boolean deleteDocument(String documentId) {
        Optional<DocumentMetadataEntity> entity = documentMetadataRepository.findByDocumentId(documentId);
        if (entity.isPresent()) {
            documentMetadataRepository.deleteByDocumentId(documentId);
            log.info("Deleted document metadata for {}", documentId);
            return true;
        }
        return false;
    }

    public List<DocumentMetadataEntity> listAllDocuments() {
        return documentMetadataRepository.findAll();
    }
}
