package com.aegispulse.engine.config;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;

public class PlaceholderChatModel implements ChatModel {

    private final String modelName;

    public PlaceholderChatModel(String modelName) {
        this.modelName = modelName;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        String msg = String.format("[%s]: The requested model service is currently offline or unreachable. Please verify model service status.", modelName);
        return new ChatResponse(List.of(new Generation(new AssistantMessage(msg))));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.just(call(prompt));
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return null;
    }
}
