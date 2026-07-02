package com.becs.processor.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "becs_file_header")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class FileHeader {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bpy_file_id", nullable = false)
    @ToString.Exclude
    private BpyFile bpyFile;

    @Column(name = "reel_sequence_number", length = 2)
    private String reelSequenceNumber;

    @Column(name = "financial_institution", length = 3)
    private String financialInstitution;

    @Column(name = "user_preferred_spec", length = 26)
    private String userPreferredSpec;

    @Column(name = "user_id", length = 9)
    private String userId;

    @Column(name = "description", length = 12)
    private String description;

    @Column(name = "processing_date")
    private LocalDate processingDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
