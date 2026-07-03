package com.becs.processor.controller;

import com.becs.processor.config.BecsProperties;
import com.becs.processor.model.BecsFile;
import com.becs.processor.model.BecsFileStatus;
import com.becs.processor.model.FileType;
import com.becs.processor.repository.BecsFileRepository;
import com.becs.processor.repository.PaymentRecordRepository;
import com.becs.processor.service.BecsProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/becs")
@RequiredArgsConstructor
@Tag(name = "BECS File Processor")
public class BecsController {

    private final BecsFileRepository     becsFileRepo;
    private final PaymentRecordRepository paymentRepo;
    private final BecsProcessingService  processingService;
    private final BecsProperties        props;

    /** List all registered BPY files, optionally filtered by status */
    @GetMapping("/files")
    @Operation(summary = "List processed BPY files")
    public List<BecsFile> listFiles(
            @RequestParam(required = false) BecsFileStatus status) {
        if (status != null) return becsFileRepo.findByStatusOrderByReceivedAtAsc(status);
        return becsFileRepo.findAll();
    }

    /** Get a single BPY file record */
    @GetMapping("/files/{id}")
    @Operation(summary = "Get BPY file by ID")
    public ResponseEntity<BecsFile> getFile(@PathVariable Long id) {
        return becsFileRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Payment records for a given BPY file */
    @GetMapping("/files/{id}/payments")
    @Operation(summary = "List payment records for a BPY file")
    public ResponseEntity<?> getPayments(@PathVariable Long id) {
        if (!becsFileRepo.existsById(id)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(paymentRepo.findByBecsFileId(id));
    }

    /** Summary stats for a given BPY file */
    @GetMapping("/files/{id}/summary")
    @Operation(summary = "Summary (record count, totals) for a BPY file")
    public ResponseEntity<?> getSummary(@PathVariable Long id) {
        if (!becsFileRepo.existsById(id)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of(
                "recordCount",    paymentRepo.countByBecsFileId(id),
                "totalCreditsCents", orZero(paymentRepo.sumCreditsByFileId(id)),
                "totalDebitsCents",  orZero(paymentRepo.sumDebitsByFileId(id))
        ));
    }

    /**
     * Upload a BPY or RET file directly via the API.
     * The file is saved to the matching inbox subfolder and then immediately processed.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a BPY or RET file for immediate processing")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("No file provided");
        }
        String originalName = file.getOriginalFilename();
        FileType fileType = detectFileType(originalName);
        if (fileType == null) {
            return ResponseEntity.badRequest().body("File name must match {bsb_number}.bpy.nnn, {bsb_number}.ret.nnn or {bsb_number}.nde.nnn");
        }

        Path dest = inboxFor(fileType).resolve(originalName);
        Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
        log.info("Uploaded {} ({} bytes) to {} inbox", originalName, file.getSize(), fileType);

        BecsFile result = processingService.process(dest, fileType);
        return ResponseEntity.accepted().body(result);
    }

    /**
     * Manually re-trigger processing for an unprocessed file in the inbox.
     */
    @PostMapping("/trigger/{fileName}")
    @Operation(summary = "Manually trigger processing of a specific inbox file")
    public ResponseEntity<?> trigger(@PathVariable String fileName) throws IOException {
        FileType fileType = detectFileType(fileName);
        if (fileType == null) {
            return ResponseEntity.badRequest().body("File name must match {bsb_number}.bpy.nnn, {bsb_number}.ret.nnn or {bsb_number}.nde.nnn");
        }

        Path target = inboxFor(fileType).resolve(fileName);
        if (!Files.exists(target)) {
            return ResponseEntity.notFound().build();
        }
        BecsFile result = processingService.process(target, fileType);
        return ResponseEntity.accepted().body(result);
    }

    private Path inboxFor(FileType fileType) {
        return switch (fileType) {
            case RET -> props.inboxRet();
            case NDE -> props.inboxNde();
            case BPY -> props.inboxBpy();
        };
    }

    private FileType detectFileType(String fileName) {
        if (fileName == null) return null;
        String lower = fileName.toLowerCase();
        if (lower.matches("\\d+\\.bpy\\.\\d{3}")) return FileType.BPY;
        if (lower.matches("\\d+\\.ret\\.\\d{3}")) return FileType.RET;
        if (lower.matches("\\d+\\.nde\\.\\d{3}")) return FileType.NDE;
        return null;
    }

    private long orZero(Long v) { return v == null ? 0L : v; }
}
