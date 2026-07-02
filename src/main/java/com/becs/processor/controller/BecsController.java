package com.becs.processor.controller;

import com.becs.processor.config.BecsProperties;
import com.becs.processor.model.BpyFile;
import com.becs.processor.model.BpyFileStatus;
import com.becs.processor.repository.BpyFileRepository;
import com.becs.processor.repository.PaymentRecordRepository;
import com.becs.processor.service.BpyProcessingService;
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
@Tag(name = "BECS BPY File Processor")
public class BecsController {

    private final BpyFileRepository     bpyFileRepo;
    private final PaymentRecordRepository paymentRepo;
    private final BpyProcessingService  processingService;
    private final BecsProperties        props;

    /** List all registered BPY files, optionally filtered by status */
    @GetMapping("/files")
    @Operation(summary = "List processed BPY files")
    public List<BpyFile> listFiles(
            @RequestParam(required = false) BpyFileStatus status) {
        if (status != null) return bpyFileRepo.findByStatusOrderByReceivedAtAsc(status);
        return bpyFileRepo.findAll();
    }

    /** Get a single BPY file record */
    @GetMapping("/files/{id}")
    @Operation(summary = "Get BPY file by ID")
    public ResponseEntity<BpyFile> getFile(@PathVariable Long id) {
        return bpyFileRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Payment records for a given BPY file */
    @GetMapping("/files/{id}/payments")
    @Operation(summary = "List payment records for a BPY file")
    public ResponseEntity<?> getPayments(@PathVariable Long id) {
        if (!bpyFileRepo.existsById(id)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(paymentRepo.findByBpyFileId(id));
    }

    /** Summary stats for a given BPY file */
    @GetMapping("/files/{id}/summary")
    @Operation(summary = "Summary (record count, totals) for a BPY file")
    public ResponseEntity<?> getSummary(@PathVariable Long id) {
        if (!bpyFileRepo.existsById(id)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of(
                "recordCount",    paymentRepo.countByBpyFileId(id),
                "totalCreditsCents", orZero(paymentRepo.sumCreditsByFileId(id)),
                "totalDebitsCents",  orZero(paymentRepo.sumDebitsByFileId(id))
        ));
    }

    /**
     * Upload a BPY file directly via the API.
     * The file is saved to the inbox directory and then immediately processed.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a BPY file for immediate processing")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("No file provided");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".bpy")) {
            return ResponseEntity.badRequest().body("File must have a .bpy extension");
        }

        Path dest = props.inbox().resolve(originalName);
        Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
        log.info("Uploaded {} ({} bytes) to inbox", originalName, file.getSize());

        BpyFile result = processingService.process(dest);
        return ResponseEntity.accepted().body(result);
    }

    /**
     * Manually re-trigger processing for an unprocessed file in the inbox.
     */
    @PostMapping("/trigger/{fileName}")
    @Operation(summary = "Manually trigger processing of a specific inbox file")
    public ResponseEntity<?> trigger(@PathVariable String fileName) throws IOException {
        Path target = props.inbox().resolve(fileName);
        if (!Files.exists(target)) {
            return ResponseEntity.notFound().build();
        }
        BpyFile result = processingService.process(target);
        return ResponseEntity.accepted().body(result);
    }

    private long orZero(Long v) { return v == null ? 0L : v; }
}
