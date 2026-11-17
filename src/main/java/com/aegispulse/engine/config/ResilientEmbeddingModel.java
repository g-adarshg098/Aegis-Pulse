package com.aegispulse.engine.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ResilientEmbeddingModel implements EmbeddingModel {

    private final EmbeddingModel delegate;
    private final int dimensions;

    public ResilientEmbeddingModel(EmbeddingModel delegate, int dimensions) {
        this.delegate = delegate;
        this.dimensions = dimensions;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        if (delegate != null) {
            try {
                return delegate.call(request);
            } catch (Exception ex) {
                log.warn("Delegate EmbeddingModel failed ({}), generating deterministic synthetic embeddings.", ex.getMessage());
            }
        }
        List<Embedding> embeddings = new ArrayList<>();
        List<String> inputs = request.getInstructions();
        for (int i = 0; i < inputs.size(); i++) {
            float[] vector = generateDeterministicVector(inputs.get(i), dimensions);
            embeddings.add(new Embedding(vector, i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        if (delegate != null) {
            try {
                return delegate.embed(document);
            } catch (Exception ex) {
                log.warn("Delegate embed(Document) failed ({}), using fallback embedding.", ex.getMessage());
            }
        }
        return generateDeterministicVector(document.getText(), dimensions);
    }

    @Override
    public float[] embed(String text) {
        if (delegate != null) {
            try {
                return delegate.embed(text);
            } catch (Exception ex) {
                log.warn("Delegate embed(String) failed ({}), using fallback embedding.", ex.getMessage());
            }
        }
        return generateDeterministicVector(text, dimensions);
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    private static float[] generateDeterministicVector(String text, int dim) {
        float[] vector = new float[dim];
        if (text == null || text.isBlank()) {
            return vector;
        }

        // Tokenize into words and compute bag-of-words hash embedding
        String[] tokens = text.toLowerCase().split("\\W+");
        for (String token : tokens) {
            if (token.isBlank()) continue;
            int hash1 = Math.abs(token.hashCode()) % dim;
            vector[hash1] += 1.0f;

            // Add subword / prefix hashes for partial matching
            if (token.length() > 3) {
                int hash2 = Math.abs((token.substring(0, 4) + "_pre").hashCode()) % dim;
                vector[hash2] += 0.5f;
            }
        }

        // Normalize to unit length for cosine similarity
        float norm = 0.0f;
        for (float v : vector) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < dim; i++) {
                vector[i] /= norm;
            }
        }

        return vector;
    }
}
