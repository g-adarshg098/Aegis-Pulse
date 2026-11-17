package com.aegispulse.engine.config;

import com.aegispulse.engine.tool.KnowledgeBaseTool;
import com.aegispulse.engine.tool.SystemPulseTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Slf4j
@Configuration
public class AiConfig {

    @Value("${spring.ai.vectorstore.pgvector.vector-dimensions:1536}")
    private int vectorDimensions;

    @Bean
    @Primary
    public ChatClient groqChatClient(@Qualifier("openAiChatModel") ObjectProvider<ChatModel> openAiChatModelProvider,
                                     KnowledgeBaseTool knowledgeBaseTool,
                                     SystemPulseTool systemPulseTool) {
        ChatModel model = openAiChatModelProvider.getIfAvailable();
        if (model == null) {
            log.warn("openAiChatModel is not available. Creating placeholder Groq client.");
            return ChatClient.builder(new PlaceholderChatModel("Groq Cloud (Offline)"))
                    .defaultSystem("You are Groq, an ultra-fast enterprise AI assistant.")
                    .build();
        }
        return ChatClient.builder(model)
                .defaultSystem("You are Groq, an ultra-fast enterprise AI assistant powering the Aegis Pulse Engine. " +
                        "Provide crisp, highly accurate, enterprise-grade answers. Use available tools when appropriate.")
                .defaultTools(knowledgeBaseTool, systemPulseTool)
                .build();
    }

    @Bean
    public ChatClient ollamaChatClient(@Qualifier("ollamaChatModel") ObjectProvider<ChatModel> ollamaChatModelProvider,
                                       KnowledgeBaseTool knowledgeBaseTool,
                                       SystemPulseTool systemPulseTool) {
        ChatModel model = null;
        try {
            model = ollamaChatModelProvider.getIfAvailable();
        } catch (Exception ex) {
            log.warn("OllamaChatModel unavailable: {}", ex.getMessage());
        }

        if (model == null) {
            log.warn("ollamaChatModel is not available. Creating placeholder Ollama client.");
            return ChatClient.builder(new PlaceholderChatModel("Ollama Local (Offline)"))
                    .defaultSystem("You are Ollama, a secure local enterprise AI assistant.")
                    .build();
        }
        return ChatClient.builder(model)
                .defaultSystem("You are Ollama, a secure local enterprise AI assistant running on private infrastructure. " +
                        "Provide confidential, highly secure, enterprise-grade answers. Use available tools when appropriate.")
                .defaultTools(knowledgeBaseTool, systemPulseTool)
                .build();
    }

    @Bean
    @Primary
    public EmbeddingModel primaryEmbeddingModel(@Qualifier("ollamaEmbeddingModel") ObjectProvider<EmbeddingModel> ollamaEmbeddingModelProvider) {
        EmbeddingModel delegate = null;
        try {
            delegate = ollamaEmbeddingModelProvider.getIfAvailable();
        } catch (Exception ex) {
            log.warn("ollamaEmbeddingModel is not available: {}. Using resilient fallback.", ex.getMessage());
        }
        return new ResilientEmbeddingModel(delegate, vectorDimensions);
    }

    @Bean
    @Primary
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        try {
            log.info("Attempting to initialize PgVectorStore with dimension {}...", vectorDimensions);
            PgVectorStore store = PgVectorStore.builder(jdbcTemplate, embeddingModel)
                    .dimensions(vectorDimensions)
                    .initializeSchema(true)
                    .build();
            store.afterPropertiesSet();
            log.info("PgVectorStore successfully initialized.");
            return store;
        } catch (Exception ex) {
            log.warn("PostgreSQL pgvector initialization failed ({}). Gracefully falling back to SimpleVectorStore.", ex.getMessage());
            return SimpleVectorStore.builder(embeddingModel).build();
        }
    }
}

