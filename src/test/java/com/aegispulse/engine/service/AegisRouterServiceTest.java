package com.aegispulse.engine.service;

import com.aegispulse.engine.dto.ChatRequest;
import com.aegispulse.engine.guardrail.SecurityGuardrailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.*;

class AegisRouterServiceTest {

    private ChatClient groqClient;
    private ChatClient ollamaClient;
    private SecurityGuardrailService guardrailService;
    private AegisRouterService routerService;

    @BeforeEach
    void setUp() {
        groqClient = Mockito.mock(ChatClient.class);
        ollamaClient = Mockito.mock(ChatClient.class);
        guardrailService = new SecurityGuardrailService();
        routerService = new AegisRouterService(groqClient, ollamaClient, guardrailService);
    }

    @Test
    void testStandardPromptRoutesToGroq() {
        ChatRequest request = ChatRequest.builder()
                .prompt("Summarize reactive programming in 20 words.")
                .build();

        AegisRouterService.RoutingDecision decision = routerService.determineRoute(request);
        assertEquals("groq-cloud", decision.getTargetModel());
        assertFalse(decision.isConfidential());
        assertSame(groqClient, decision.getChatClient());
    }

    @Test
    void testConfidentialPromptRoutesToOllama() {
        ChatRequest request = ChatRequest.builder()
                .prompt("Here is our trade secret algorithm design.")
                .build();

        AegisRouterService.RoutingDecision decision = routerService.determineRoute(request);
        assertEquals("ollama-local", decision.getTargetModel());
        assertTrue(decision.isConfidential());
        assertSame(ollamaClient, decision.getChatClient());
    }

    @Test
    void testExplicitOllamaOverride() {
        ChatRequest request = ChatRequest.builder()
                .prompt("Hello world")
                .modelOverride("ollama")
                .build();

        AegisRouterService.RoutingDecision decision = routerService.determineRoute(request);
        assertEquals("ollama-local", decision.getTargetModel());
        assertSame(ollamaClient, decision.getChatClient());
    }
}
