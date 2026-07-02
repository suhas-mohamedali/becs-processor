package com.becs.processor.service;

import com.becs.processor.dto.ParsedBpyFile;
import com.becs.processor.dto.ParsedHeader;
import com.becs.processor.dto.ParsedPayment;
import com.becs.processor.dto.ParsedTrailer;
import com.becs.processor.model.*;
import com.becs.processor.parser.BpyFileParser;
import com.becs.processor.repository.BpyFileRepository;
import com.becs.processor.repository.PaymentRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Orchestrates the full debulk pipeline for a single BPY file:
 *  1. Register the file in the DB
 *  2. Parse the fixed-width BECS DE content
 *  3. Group detail records by BSB → one debulked output file per BSB
 *  4. Persist each PaymentRecord to the DB
 *  5. Archive the source file
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class  BpyProcessingService {

    private final BpyFileParser         parser;
    private final FileStorageService    fileStorage;
    private final BpyFileRepository     bpyFileRepo;
    private final PaymentRecordRepository paymentRepo;

    /**
     * Process a BPY file found in the inbox directory.
     *
     * @param inboxPath full path to the .bpy file
     */
    @Transactional
    public BpyFile process(Path inboxPath) {
        String fileName = inboxPath.getFileName().toString();
        log.info("Processing BPY file: {}", fileName);

        // ---- 1. Register / retrieve DB record ----
        BpyFile bpyFile = bpyFileRepo.findByFileName(fileName).orElseGet(() ->
                bpyFileRepo.save(BpyFile.builder()
                        .fileName(fileName)
                        .filePath(inboxPath.toAbsolutePath().toString())
                        .fileSizeBytes(safeSize(inboxPath))
                        .receivedAt(LocalDateTime.now())
                        .status(BpyFileStatus.RECEIVED)
                        .build())
        );

        if (bpyFile.getStatus() == BpyFileStatus.COMPLETED) {
            log.warn("File {} already processed – skipping", fileName);
            return bpyFile;
        }

        bpyFile.setStatus(BpyFileStatus.PROCESSING);
        bpyFileRepo.save(bpyFile);

        try {
            // ---- 2. Parse ----
            ParsedBpyFile parsed = parser.parse(inboxPath);

            // ---- 3. Save every header found (a file may contain multiple reels) ----
            List<FileHeader> headers = new ArrayList<>();
            for (ParsedHeader ph : parsed.getHeaders()) {
                headers.add(FileHeader.builder()
                        .bpyFile(bpyFile)
                        .reelSequenceNumber(ph.getReelSequenceNumber())
                        .financialInstitution(ph.getFinancialInstitution())
                        .userPreferredSpec(ph.getUserPreferredSpec())
                        .userId(ph.getUserId())
                        .description(ph.getDescription())
                        .processingDate(ph.getProcessingDate())
                        .build());
            }
            bpyFile.setFileHeaders(headers);

            // ---- 4. Save every trailer found (one per reel) ----
            List<FileTrailer> trailers = new ArrayList<>();
            for (ParsedTrailer pt : parsed.getTrailers()) {
                trailers.add(FileTrailer.builder()
                        .bpyFile(bpyFile)
                        .bsbFiller(pt.getBsbFiller())
                        .netTotalAmount(pt.getNetTotalAmount())
                        .creditTotalAmount(pt.getCreditTotalAmount())
                        .debitTotalAmount(pt.getDebitTotalAmount())
                        .recordCount(pt.getRecordCount())
                        .build());
            }
            bpyFile.setFileTrailers(trailers);

            // ---- 5. Write the debulked file (last header + all details + last trailer) ----
            List<ParsedPayment> payments = parsed.getPayments();
            ParsedHeader  lastHeader  = headers.isEmpty()  ? null : parsed.getHeaders().get(parsed.getHeaders().size() - 1);
            ParsedTrailer lastTrailer = trailers.isEmpty() ? null : parsed.getTrailers().get(parsed.getTrailers().size() - 1);
            Path outPath = fileStorage.writeDebulkedFile(fileName, lastHeader, payments, lastTrailer);
            String outStr = outPath.toAbsolutePath().toString();
            bpyFile.setBpyOutputFilePath(outStr);
            bpyFile.setBpyOutFileName(outPath.getFileName().toString());

            List<PaymentRecord> records = new ArrayList<>();
            for (ParsedPayment p : payments) {
                records.add(PaymentRecord.builder()
                        .bpyFile(bpyFile)
                        .bsbNumber(p.getBsbNumber())
                        .accountNumber(p.getAccountNumber())
                        .indicator(p.getIndicator())
                        .transactionCode(p.getTransactionCode())
                        .amountCents(p.getAmountCents())
                        .accountName(p.getAccountName())
                        .lodgementReference(p.getLodgementReference())
                        .traceBsb(p.getTraceBsb())
                        .traceAccount(p.getTraceAccount())
                        .remitterName(p.getRemitterName())
                        .withholdingTax(p.getWithholdingTax())
                        .lineNumber(p.getLineNumber())
                        .recordType(p.getRecordType())
                        .outputFilePath(outStr)
                        .status(PaymentStatus.STORED)
                        .build());
            }

            paymentRepo.saveAll(records);

            // ---- 6. Archive source file ----
            Path archivePath = fileStorage.archiveSourceFile(inboxPath);

            // ---- 7. Mark completed ----
            bpyFile.setStatus(BpyFileStatus.COMPLETED);
            bpyFile.setProcessedAt(LocalDateTime.now());
            bpyFile.setRecordCount(records.size());
            bpyFile.setBpyRecordCount(records.size());
            bpyFile.setArchivedPath(archivePath.toAbsolutePath().toString());
            bpyFileRepo.save(bpyFile);

            log.info("Completed processing {}: {} payment records",
                    fileName, records.size());

        } catch (Exception e) {
            log.error("Failed to process {}: {}", fileName, e.getMessage(), e);
            bpyFile.setStatus(BpyFileStatus.FAILED);
            bpyFile.setErrorMessage(e.getMessage());
            bpyFileRepo.save(bpyFile);

            try { fileStorage.moveToError(inboxPath); }
            catch (Exception ex) { log.error("Cannot move file to error dir: {}", ex.getMessage()); }
        }

        return bpyFile;
    }

    private long safeSize(Path p) {
        try { return Files.size(p); } catch (Exception e) { return -1L; }
    }
}
