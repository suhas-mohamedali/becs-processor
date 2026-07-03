package com.becs.processor.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "becs_file_trailer")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class FileTrailer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "becs_file_id", nullable = false)
    @ToString.Exclude
    private BecsFile becsFile;

    @Column(name = "bsb_filler", length = 7)
    private String bsbFiller;

    @Column(name = "net_total_amount")
    private Long netTotalAmount;

    @Column(name = "credit_total_amount")
    private Long creditTotalAmount;

    @Column(name = "debit_total_amount")
    private Long debitTotalAmount;

    @Column(name = "record_count")
    private Integer recordCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
