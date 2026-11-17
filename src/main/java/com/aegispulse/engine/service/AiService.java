package com.aegispulse.engine.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class AiService {

    private final ChatClient groqChatClient;
    private final ChatClient ollamaChatClient;

    public AiService(@Qualifier("groqChatClient") ChatClient groqChatClient,
                     @Qualifier("ollamaChatClient") ChatClient ollamaChatClient) {
        this.groqChatClient = groqChatClient;
        this.ollamaChatClient = ollamaChatClient;
    }

    public Flux<String> streamChatWithGroq(String userInput) {
        return this.groqChatClient.prompt()
                .user(userInput)
                .stream()
                .content();
    }

    public Flux<String> streamChatWithOllama(String userInput) {
        return this.ollamaChatClient.prompt()
                .user(userInput)
                .stream()
                .content();
    }
}