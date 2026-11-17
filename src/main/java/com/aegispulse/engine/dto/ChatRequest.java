package com.aegispulse.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {
    private String prompt;
    private String sessionId;
    private Boolean confidential;
    private String modelOverride; // "auto", "groq", "ollama"
    @Builder.Default
    private Boolean useRag = true;
}
