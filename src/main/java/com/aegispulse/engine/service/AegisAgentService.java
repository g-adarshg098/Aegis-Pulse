package com.aegispulse.engine.service;

import com.aegispulse.engine.dto.ChatRequest;
import com.aegispulse.engine.dto.ChatResponse;
import com.aegispulse.engine.dto.ChatResponse.Citation;
import com.aegispulse.engine.entity.AuditLogEntity;
import com.aegispulse.engine.memory.DualTierChatMemoryService;
import com.aegispulse.engine.rag.RagSearchService;
import com.aegispulse.engine.repository.AuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class AegisAgentService {

    private final AegisRouterService routerService;
    private final RagSearchService ragSearchService;
    private final DualTierChatMemoryService chatMemoryService;
    private final AuditLogRepository auditLogRepository;

    public AegisAgentService(AegisRouterService routerService,
                             RagSearchService ragSearchService,
                             DualTierChatMemoryService chatMemoryService,
                             AuditLogRepository auditLogRepository) {
        this.routerService = routerService;
        this.ragSearchService = ragSearchService;
        this.chatMemoryService = chatMemoryService;
        this.auditLogRepository = auditLogRepository;
    }

    public ChatResponse chat(ChatRequest request) {
        long startTime = System.currentTimeMillis();
        String sessionId = (request.getSessionId() != null && !request.getSessionId().isBlank())
                ? request.getSessionId()
                : "default-session";

        AegisRouterService.RoutingDecision route = routerService.determineRoute(request);
        ChatClient activeClient = route.getChatClient();
        String modelUsed = route.getTargetModel();
        boolean fallbackOccurred = false;

        // 1. RAG retrieval if enabled
        List<Citation> citations = new ArrayList<>();
        String ragContext = "";
        if (Boolean.TRUE.equals(request.getUseRag())) {
            List<Document> docs = ragSearchService.searchDocuments(request.getPrompt(), 3, 0.05);
            if (!docs.isEmpty()) {
                ragContext = ragSearchService.formatRagContext(docs);
                citations = ragSearchService.extractCitations(docs);
            }
        }

        // 2. Multi-turn conversation history
        List<Message> history = chatMemoryService.get(sessionId, 6);

        // 3. Compose prompt with history and RAG context
        String fullUserPrompt = request.getPrompt();
        if (!ragContext.isBlank()) {
            fullUserPrompt = fullUserPrompt + "\n" + ragContext;
        }

        String assistantText;
        try {
            assistantText = executeChatClient(activeClient, history, fullUserPrompt);
        } catch (Exception ex) {
            log.warn("Primary model ({}) failed: {}. Initiating auto-failover to Ollama...", modelUsed, ex.getMessage());
            try {
                activeClient = routerService.getFallbackClient();
                modelUsed = "ollama-local (failover)";
                fallbackOccurred = true;
                assistantText = executeChatClient(activeClient, history, fullUserPrompt);
            } catch (Exception fallbackEx) {
                log.error("Failover also failed: {}", fallbackEx.getMessage());
                recordAudit(sessionId, request.getPrompt(), modelUsed, route.isConfidential(),
                        true, System.currentTimeMillis() - startTime, "FAILED", fallbackEx.getMessage());
                throw new RuntimeException("AI inference failed on both primary and failover models: " + fallbackEx.getMessage(), fallbackEx);
            }
        }

        long executionTime = System.currentTimeMillis() - startTime;

        // 4. Update memory
        chatMemoryService.add(sessionId, List.of(
                new UserMessage(request.getPrompt()),
                new AssistantMessage(assistantText)
        ));

        // 5. Audit log
        recordAudit(sessionId, request.getPrompt(), modelUsed, route.isConfidential(),
                fallbackOccurred, executionTime, "SUCCESS", null);

        return ChatResponse.builder()
                .response(assistantText)
                .sessionId(sessionId)
                .modelUsed(modelUsed)
                .confidential(route.isConfidential())
                .executionTimeMs(executionTime)
                .citations(citations)
                .build();
    }

    public Flux<String> streamChat(ChatRequest request) {
        long startTime = System.currentTimeMillis();
        String sessionId = (request.getSessionId() != null && !request.getSessionId().isBlank())
                ? request.getSessionId()
                : "default-session";

        AegisRouterService.RoutingDecision route = routerService.determineRoute(request);
        ChatClient activeClient = route.getChatClient();
        String modelUsed = route.getTargetModel();

        // RAG context
        String ragContext = "";
        if (Boolean.TRUE.equals(request.getUseRag())) {
            List<Document> docs = ragSearchService.searchDocuments(request.getPrompt(), 3, 0.05);
            if (!docs.isEmpty()) {
                ragContext = ragSearchService.formatRagContext(docs);
            }
        }

        List<Message> history = chatMemoryService.get(sessionId, 6);
        final String composedPrompt = ragContext.isBlank() 
                ? request.getPrompt() 
                : request.getPrompt() + "\n" + ragContext;
        final String finalModelUsed = route.getTargetModel();

        StringBuilder responseCollector = new StringBuilder();

        try {
            return activeClient.prompt()
                    .messages(history)
                    .user(composedPrompt)
                    .stream()
                    .content()
                    .doOnNext(responseCollector::append)
                    .doOnComplete(() -> {
                        long executionTime = System.currentTimeMillis() - startTime;
                        chatMemoryService.add(sessionId, List.of(
                                new UserMessage(request.getPrompt()),
                                new AssistantMessage(responseCollector.toString())
                        ));
                        recordAudit(sessionId, request.getPrompt(), finalModelUsed, route.isConfidential(),
                                false, executionTime, "SUCCESS", null);
                    })
                    .onErrorResume(err -> {
                        log.warn("Streaming with primary model failed, attempting failover: {}", err.getMessage());
                        ChatClient fallbackClient = routerService.getFallbackClient();
                        return fallbackClient.prompt()
                                .messages(history)
                                .user(composedPrompt)
                                .stream()
                                .content()
                                .doOnNext(responseCollector::append)
                                .doOnComplete(() -> {
                                    long executionTime = System.currentTimeMillis() - startTime;
                                    chatMemoryService.add(sessionId, List.of(
                                            new UserMessage(request.getPrompt()),
                                            new AssistantMessage(responseCollector.toString())
                                    ));
                                    recordAudit(sessionId, request.getPrompt(), "ollama-local (failover)",
                                            route.isConfidential(), true, executionTime, "SUCCESS", null);
                                });
                    });
        } catch (Exception e) {
            log.error("Failed to initiate streaming: {}", e.getMessage());
            return Flux.just("Error initiating stream: " + e.getMessage());
        }
    }

    private String executeChatClient(ChatClient client, List<Message> history, String userPrompt) {
        return client.prompt()
                .messages(history)
                .user(userPrompt)
                .call()
                .content();
    }

    private void recordAudit(String sessionId, String prompt, String modelUsed,
                             boolean confidential, boolean fallback, long latencyMs,
                             String status, String errorDetails) {
        try {
            AuditLogEntity audit = AuditLogEntity.builder()
                    .sessionId(sessionId)
                    .prompt(prompt)
                    .routedModel(modelUsed)
                    .confidential(confidential)
                    .fallbackOccurred(fallback)
                    .executionTimeMs(latencyMs)
                    .status(status)
                    .errorDetails(errorDetails)
                    .timestamp(LocalDateTime.now())
                    .build();
            auditLogRepository.save(audit);
        } catch (Exception e) {
            log.warn("Audit logging to database failed: {}", e.getMessage());
        }
    }
}
