package com.becs.processor.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "becs_payment_record")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class PaymentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bpy_file_id", nullable = false)
    @ToString.Exclude
    private BpyFile bpyFile;

    // ---- BECS DE Standard Fields ----
    @Column(name = "bsb_number", length = 7)
    private String bsbNumber;

    @Column(name = "account_number", length = 9)
    private String accountNumber;

    @Column(name = "indicator", columnDefinition = "CHAR(1)")
    private String indicator;

    @Column(name = "transaction_code", length = 2)
    private String transactionCode;

    /** Amount in cents */
    @Column(name = "amount_cents")
    private Long amountCents;

    @Column(name = "account_name", length = 32)
    private String accountName;

    @Column(name = "lodgement_reference", length = 18)
    private String lodgementReference;

    @Column(name = "trace_bsb", length = 7)
    private String traceBsb;

    @Column(name = "trace_account", length = 9)
    private String traceAccount;

    @Column(name = "remitter_name", length = 16)
    private String remitterName;

    /** Withholding tax in cents */
    @Column(name = "withholding_tax")
    private Long withholdingTax;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "record_type", columnDefinition = "CHAR(1)")
    private String recordType;

    @Column(name = "output_file_path", length = 1024)
    private String outputFilePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Lob
    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
