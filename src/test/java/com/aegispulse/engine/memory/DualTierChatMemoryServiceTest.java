package com.aegispulse.engine.memory;

import com.aegispulse.engine.entity.ChatMessageEntity;
import com.aegispulse.engine.repository.ChatMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

class DualTierChatMemoryServiceTest {

    private ChatMessageRepository chatMessageRepository;
    private DualTierChatMemoryService memoryService;

    @BeforeEach
    void setUp() {
        chatMessageRepository = Mockito.mock(ChatMessageRepository.class);
        memoryService = new DualTierChatMemoryService(chatMessageRepository, Optional.empty());
    }

    @Test
    void testAddAndGetMessages() {
        String sessionId = "test-session-123";
        List<Message> turns = List.of(
                new UserMessage("What is Aegis Pulse?"),
                new AssistantMessage("Aegis Pulse is an Enterprise RAG Engine.")
        );

        memoryService.add(sessionId, turns);

        List<Message> retrieved = memoryService.get(sessionId, 5);
        assertEquals(2, retrieved.size());
        assertEquals("What is Aegis Pulse?", retrieved.get(0).getText());
        assertEquals("Aegis Pulse is an Enterprise RAG Engine.", retrieved.get(1).getText());

        verify(chatMessageRepository, Mockito.atLeast(2)).save(any(ChatMessageEntity.class));
    }

    @Test
    void testClearSession() {
        String sessionId = "test-session-456";
        memoryService.add(sessionId, List.of(new UserMessage("Hello")));
        assertFalse(memoryService.get(sessionId, 5).isEmpty());

        memoryService.clear(sessionId);
        assertTrue(memoryService.get(sessionId, 5).isEmpty());
        verify(chatMessageRepository).deleteBySessionId(sessionId);
    }
}
