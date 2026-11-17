package com.aegispulse.engine.rag;

import com.aegispulse.engine.dto.ChatResponse.Citation;
import com.aegispulse.engine.dto.DocumentSearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class RagSearchService {

    private final VectorStore vectorStore;

    public RagSearchService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<Document> searchDocuments(String query, int topK, double minSimilarity) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        try {
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(Math.max(1, topK))
                    .similarityThreshold(minSimilarity)
                    .build();

            return vectorStore.similaritySearch(request);
        } catch (Exception e) {
            log.warn("Vector search failed or empty: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<DocumentSearchResponse> search(String query, int topK, double minSimilarity) {
        List<Document> docs = searchDocuments(query, topK, minSimilarity);
        List<DocumentSearchResponse> results = new ArrayList<>();

        for (Document d : docs) {
            results.add(DocumentSearchResponse.builder()
                    .content(d.getText())
                    .metadata(d.getMetadata())
                    .score(d.getScore())
                    .build());
        }
        return results;
    }

    public List<Citation> extractCitations(List<Document> docs) {
        List<Citation> citations = new ArrayList<>();
        if (docs == null) return citations;

        for (Document doc : docs) {
            String source = (String) doc.getMetadata().getOrDefault("filename",
                    doc.getMetadata().getOrDefault("source", "Unknown"));
            Integer chunkIndex = (Integer) doc.getMetadata().get("chunkIndex");
            String text = doc.getText();
            String snippet = (text != null && text.length() > 180)
                    ? text.substring(0, 180) + "..."
                    : text;

            citations.add(Citation.builder()
                    .source(source)
                    .chunkIndex(chunkIndex)
                    .score(doc.getScore())
                    .snippet(snippet)
                    .build());
        }
        return citations;
    }

    public String formatRagContext(List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n--- RELEVANT ENTERPRISE KNOWLEDGE CONTEXT ---\n");
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            String source = (String) doc.getMetadata().getOrDefault("filename", "Document " + (i + 1));
            sb.append(String.format("[Source: %s]\n%s\n\n", source, doc.getText()));
        }
        sb.append("---------------------------------------------\n");
        return sb.toString();
    }
}
