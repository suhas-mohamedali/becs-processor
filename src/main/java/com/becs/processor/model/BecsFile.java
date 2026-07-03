package com.becs.processor.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "becs_file")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class BecsFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false, length = 512)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 1024)
    private String filePath;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "archived_path", length = 1024)
    private String archivedPath;

    /** Whether this input file was a BPY (payment), RET (return) or NDE bundle. */
    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 10)
    private FileType fileType;

    // BPY debulked output
    @Column(name = "bpy_out_file_name", length = 512)
    private String bpyOutFileName;

    @Column(name = "bpy_output_file_path", length = 1024)
    private String bpyOutputFilePath;

    @Column(name = "bpy_record_count")
    private Integer bpyRecordCount;

    // RET debulked output
    @Column(name = "ret_out_file_name", length = 512)
    private String retOutFileName;

    @Column(name = "ret_output_file_path", length = 1024)
    private String retOutputFilePath;

    @Column(name = "ret_record_count")
    private Integer retRecordCount;

    // NDE debulked output
    @Column(name = "nde_out_file_name", length = 512)
    private String ndeOutFileName;

    @Column(name = "nde_output_file_path", length = 1024)
    private String ndeOutputFilePath;

    @Column(name = "nde_record_count")
    private Integer ndeRecordCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private BecsFileStatus status = BecsFileStatus.RECEIVED;

    @Lob
    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "record_count")
    private Integer recordCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "becsFile", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    @Builder.Default
    private List<PaymentRecord> paymentRecords = new ArrayList<>();

    @OneToMany(mappedBy = "becsFile", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @ToString.Exclude
    @Builder.Default
    private List<FileHeader> fileHeaders = new ArrayList<>();

    @OneToMany(mappedBy = "becsFile", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @ToString.Exclude
    @Builder.Default
    private List<FileTrailer> fileTrailers = new ArrayList<>();
}
