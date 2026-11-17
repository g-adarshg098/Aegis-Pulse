package com.aegispulse.engine.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "aegis_documents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentMetadataEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String documentId;

    @Column(nullable = false)
    private String filename;

    private String fileType;

    private int totalChunks;

    private long fileSize;

    private String tags;

    @Builder.Default
    private LocalDateTime uploadedAt = LocalDateTime.now();
}
