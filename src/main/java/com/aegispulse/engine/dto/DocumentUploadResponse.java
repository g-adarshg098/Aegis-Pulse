package com.aegispulse.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadResponse {
    private String documentId;
    private String filename;
    private String fileType;
    private int chunksCount;
    private String status;
    private String message;
    private LocalDateTime timestamp;
}
