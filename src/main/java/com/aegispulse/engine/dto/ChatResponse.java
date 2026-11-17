package com.aegispulse.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private String response;
    private String sessionId;
    private String modelUsed;
    private boolean confidential;
    private long executionTimeMs;
    private List<Citation> citations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Citation {
        private String source;
        private Integer chunkIndex;
        private Double score;
        private String snippet;
    }
}
