package com.aegispulse.engine.memory;

import com.aegispulse.engine.entity.ChatMessageEntity;
import com.aegispulse.engine.repository.ChatMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class DualTierChatMemoryService implements ChatMemory {

    private final ChatMessageRepository chatMessageRepository;
    private final Optional<ReactiveStringRedisTemplate> redisTemplate;
    private final Map<String, List<Message>> localCache = new ConcurrentHashMap<>();

    @Autowired
    public DualTierChatMemoryService(ChatMessageRepository chatMessageRepository,
                                     Optional<ReactiveStringRedisTemplate> redisTemplate) {
        this.chatMessageRepository = chatMessageRepository;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @Transactional
    public void add(String conversationId, List<Message> messages) {
        if (conversationId == null || messages == null || messages.isEmpty()) {
            return;
        }

        // 1. Update in-memory tier
        localCache.compute(conversationId, (k, existing) -> {
            List<Message> list = (existing != null) ? new ArrayList<>(existing) : new ArrayList<>();
            list.addAll(messages);
            return list;
        });

        // 2. Persist to PostgreSQL tier
        for (Message msg : messages) {
            try {
                String role = msg.getMessageType() != null ? msg.getMessageType().name() : "USER";
                ChatMessageEntity entity = ChatMessageEntity.builder()
                        .sessionId(conversationId)
                        .role(role)
                        .content(msg.getText())
                        .build();
                chatMessageRepository.save(entity);
            } catch (Exception e) {
                log.warn("Could not persist chat message to database: {}", e.getMessage());
            }
        }

        // 3. Cache latest turn in Redis if accessible
        redisTemplate.ifPresent(template -> {
            try {
                String redisKey = "aegis:session:" + conversationId;
                template.opsForValue().set(redisKey, String.valueOf(System.currentTimeMillis()), Duration.ofHours(24))
                        .subscribe(
                                null,
                                err -> log.debug("Redis update skipped (non-critical): {}", err.getMessage())
                        );
            } catch (Exception e) {
                log.debug("Redis operation skipped: {}", e.getMessage());
            }
        });
    }

    @Override
    public List<Message> get(String conversationId, int lastN) {
        if (conversationId == null) {
            return Collections.emptyList();
        }

        // 1. Try local cache first
        List<Message> cached = localCache.get(conversationId);
        if (cached != null && !cached.isEmpty()) {
            int fromIndex = Math.max(0, cached.size() - lastN);
            return new ArrayList<>(cached.subList(fromIndex, cached.size()));
        }

        // 2. Fallback to PostgreSQL tier
        try {
            List<ChatMessageEntity> dbEntities = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(conversationId);
            if (!dbEntities.isEmpty()) {
                List<Message> restored = new ArrayList<>();
                for (ChatMessageEntity entity : dbEntities) {
                    restored.add(toSpringAiMessage(entity));
                }
                localCache.put(conversationId, restored);

                int fromIndex = Math.max(0, restored.size() - lastN);
                return new ArrayList<>(restored.subList(fromIndex, restored.size()));
            }
        } catch (Exception e) {
            log.warn("Failed retrieving chat history from PostgreSQL: {}", e.getMessage());
        }

        return Collections.emptyList();
    }

    @Override
    @Transactional
    public void clear(String conversationId) {
        if (conversationId == null) {
            return;
        }

        localCache.remove(conversationId);

        try {
            chatMessageRepository.deleteBySessionId(conversationId);
        } catch (Exception e) {
            log.warn("Failed deleting session from PostgreSQL: {}", e.getMessage());
        }

        redisTemplate.ifPresent(template -> {
            try {
                template.delete("aegis:session:" + conversationId).subscribe();
            } catch (Exception e) {
                log.debug("Redis delete skipped: {}", e.getMessage());
            }
        });
    }

    public List<ChatMessageEntity> getFullHistoryEntities(String conversationId) {
        try {
            return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(conversationId);
        } catch (Exception e) {
            log.warn("Could not fetch full history: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private Message toSpringAiMessage(ChatMessageEntity entity) {
        String content = entity.getContent() != null ? entity.getContent() : "";
        if ("ASSISTANT".equalsIgnoreCase(entity.getRole())) {
            return new AssistantMessage(content);
        } else if ("SYSTEM".equalsIgnoreCase(entity.getRole())) {
            return new SystemMessage(content);
        } else {
            return new UserMessage(content);
        }
    }
}
