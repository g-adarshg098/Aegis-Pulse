package com.aegispulse.engine.service;

import com.aegispulse.engine.dto.ChatRequest;
import com.aegispulse.engine.guardrail.SecurityGuardrailService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AegisRouterService {

    private final ChatClient groqChatClient;
    private final ChatClient ollamaChatClient;
    private final SecurityGuardrailService securityGuardrailService;

    public AegisRouterService(@Qualifier("groqChatClient") ChatClient groqChatClient,
                              @Qualifier("ollamaChatClient") ChatClient ollamaChatClient,
                              SecurityGuardrailService securityGuardrailService) {
        this.groqChatClient = groqChatClient;
        this.ollamaChatClient = ollamaChatClient;
        this.securityGuardrailService = securityGuardrailService;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class RoutingDecision {
        private ChatClient chatClient;
        private String targetModel;
        private boolean confidential;
        private String routingReason;
    }

    public RoutingDecision determineRoute(ChatRequest request) {
        String prompt = request.getPrompt();
        boolean confidential = securityGuardrailService.isConfidential(prompt, request.getConfidential());

        // 1. Explicit Model Override
        if ("ollama".equalsIgnoreCase(request.getModelOverride())) {
            log.info("Routing to Local Ollama via explicit override.");
            return RoutingDecision.builder()
                    .chatClient(ollamaChatClient)
                    .targetModel("ollama-local")
                    .confidential(confidential)
                    .routingReason("Explicit override (ollama)")
                    .build();
        }

        if ("groq".equalsIgnoreCase(request.getModelOverride()) && !confidential) {
            log.info("Routing to Cloud Groq via explicit override.");
            return RoutingDecision.builder()
                    .chatClient(groqChatClient)
                    .targetModel("groq-cloud")
                    .confidential(false)
                    .routingReason("Explicit override (groq)")
                    .build();
        }

        // 2. Security Guardrail: Force Local Ollama if sensitive
        if (confidential) {
            log.warn("Confidential / PII content detected. Routing to Local Ollama for privacy protection.");
            return RoutingDecision.builder()
                    .chatClient(ollamaChatClient)
                    .targetModel("ollama-local")
                    .confidential(true)
                    .routingReason("Security Guardrail: Confidential / PII detected")
                    .build();
        }

        // 3. Default: High-speed Cloud Groq
        log.info("Defaulting route to Cloud Groq for high-speed inference.");
        return RoutingDecision.builder()
                .chatClient(groqChatClient)
                .targetModel("groq-cloud")
                .confidential(false)
                .routingReason("Standard speed-optimized route")
                .build();
    }

    public ChatClient getFallbackClient() {
        return ollamaChatClient;
    }
}
