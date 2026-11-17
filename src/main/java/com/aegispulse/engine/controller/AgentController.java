package com.aegispulse.engine.controller;

import com.aegispulse.engine.dto.ChatRequest;
import com.aegispulse.engine.dto.ChatResponse;
import com.aegispulse.engine.entity.ChatMessageEntity;
import com.aegispulse.engine.memory.DualTierChatMemoryService;
import com.aegispulse.engine.service.AegisAgentService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/agent")
@CrossOrigin(origins = "*")
public class AgentController {

    private final AegisAgentService aegisAgentService;
    private final DualTierChatMemoryService chatMemoryService;

    public AgentController(AegisAgentService aegisAgentService,
                           DualTierChatMemoryService chatMemoryService) {
        this.aegisAgentService = aegisAgentService;
        this.chatMemoryService = chatMemoryService;
    }

    /**
     * Standard Non-Streaming Chat Endpoint
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        ChatResponse response = aegisAgentService.chat(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Modern SSE Reactive Stream Endpoint (Supports JSON Payload & Sessions)
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChatPost(@RequestBody ChatRequest request) {
        return aegisAgentService.streamChat(request);
    }

    /**
     * SSE Stream via Query Parameters (Browser / Curl friendly)
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamGroqResponse(
            @RequestParam(defaultValue = "Explain reactive streams in 30 words") String message,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) Boolean confidential,
            @RequestParam(required = false) String model,
            @RequestParam(defaultValue = "true") Boolean useRag) {

        ChatRequest request = ChatRequest.builder()
                .prompt(message)
                .sessionId(sessionId)
                .confidential(confidential)
                .modelOverride(model)
                .useRag(useRag)
                .build();

        return aegisAgentService.streamChat(request);
    }

    /**
     * Legacy Ollama Stream Endpoint (preserved for backward compatibility)
     */
    @GetMapping(value = "/stream/local", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamOllamaResponse(
            @RequestParam(defaultValue = "Hello from Ollama") String message,
            @RequestParam(required = false) String sessionId) {

        ChatRequest request = ChatRequest.builder()
                .prompt(message)
                .sessionId(sessionId)
                .modelOverride("ollama")
                .useRag(true)
                .build();

        return aegisAgentService.streamChat(request);
    }

    /**
     * Get multi-turn session history
     */
    @GetMapping("/sessions/{sessionId}/history")
    public ResponseEntity<List<ChatMessageEntity>> getSessionHistory(@PathVariable String sessionId) {
        return ResponseEntity.ok(chatMemoryService.getFullHistoryEntities(sessionId));
    }

    /**
     * Clear multi-turn session history
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, String>> clearSession(@PathVariable String sessionId) {
        chatMemoryService.clear(sessionId);
        return ResponseEntity.ok(Map.of("message", "Session " + sessionId + " cleared successfully"));
    }
}
