package com.aegispulse.engine.tool;

import com.aegispulse.engine.rag.RagSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class KnowledgeBaseTool {

    private final RagSearchService ragSearchService;

    public KnowledgeBaseTool(RagSearchService ragSearchService) {
        this.ragSearchService = ragSearchService;
    }

    @Tool(name = "searchKnowledgeBase", description = "Searches the enterprise vector knowledge base for relevant documents and technical context given a search query.")
    public String searchKnowledgeBase(String query) {
        log.info("Agent invoked tool 'searchKnowledgeBase' with query: {}", query);
        List<Document> docs = ragSearchService.searchDocuments(query, 3, 0.05);
        if (docs.isEmpty()) {
            return "No matching documents found in the enterprise knowledge base for: " + query;
        }

        StringBuilder sb = new StringBuilder("Found ").append(docs.size()).append(" matching document(s):\n");
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            String source = (String) doc.getMetadata().getOrDefault("filename", "Doc-" + (i + 1));
            sb.append("[").append(i + 1).append("] Source: ").append(source).append("\n")
              .append(doc.getText()).append("\n\n");
        }
        return sb.toString();
    }
}
