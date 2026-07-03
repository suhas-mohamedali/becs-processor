package com.becs.processor.service;

import com.becs.processor.dto.ParsedBecsFile;
import com.becs.processor.dto.ParsedHeader;
import com.becs.processor.dto.ParsedPayment;
import com.becs.processor.dto.ParsedTrailer;
import com.becs.processor.model.*;
import com.becs.processor.parser.BecsFileParser;
import com.becs.processor.repository.BecsFileRepository;
import com.becs.processor.repository.PaymentRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Orchestrates the full debulk pipeline for a single BPY or RET file:
 *  1. Register the file in the DB
 *  2. Parse the fixed-width BECS DE content
 *  3. Write a single debulked output file
 *  4. Persist each PaymentRecord to the DB
 *  5. Archive the source file
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class  BecsProcessingService {

    private final BecsFileParser        parser;
    private final FileStorageService    fileStorage;
    private final BecsFileRepository     becsFileRepo;
    private final PaymentRecordRepository paymentRepo;

    /**
     * Process a BPY or RET file found in the inbox directory.
     *
     * @param inboxPath full path to the input file
     * @param fileType  BPY or RET — determines the debulked output naming,
     *                  sequence bucket, and which set of columns (bpy_ or ret_) get updated
     */
    @Transactional
    public BecsFile process(Path inboxPath, FileType fileType) {
        String fileName = inboxPath.getFileName().toString();
        log.info("Processing {} file: {}", fileType, fileName);

        // ---- 1. Register / retrieve DB record ----
        BecsFile becsFile = becsFileRepo.findByFileName(fileName).orElseGet(() ->
                becsFileRepo.save(BecsFile.builder()
                        .fileName(fileName)
                        .filePath(inboxPath.toAbsolutePath().toString())
                        .receivedAt(LocalDateTime.now())
                        .fileType(fileType)
                        .status(BecsFileStatus.RECEIVED)
                        .build())
        );

        if (becsFile.getStatus() == BecsFileStatus.COMPLETED) {
            log.warn("File {} already processed – skipping", fileName);
            return becsFile;
        }

        becsFile.setStatus(BecsFileStatus.PROCESSING);
        becsFileRepo.save(becsFile);

        try {
            // ---- 2. Parse ----
            ParsedBecsFile parsed = parser.parse(inboxPath, fileType);

            // ---- 3. Save every header found (a file may contain multiple reels) ----
            List<FileHeader> headers = new ArrayList<>();
            for (ParsedHeader ph : parsed.getHeaders()) {
                headers.add(FileHeader.builder()
                        .becsFile(becsFile)
                        .reelSequenceNumber(ph.getReelSequenceNumber())
                        .financialInstitution(ph.getFinancialInstitution())
                        .userPreferredSpec(ph.getUserPreferredSpec())
                        .userId(ph.getUserId())
                        .description(ph.getDescription())
                        .processingDate(ph.getProcessingDate())
                        .build());
            }
            becsFile.setFileHeaders(headers);

            // ---- 4. Save every trailer found (one per reel) ----
            List<FileTrailer> trailers = new ArrayList<>();
            for (ParsedTrailer pt : parsed.getTrailers()) {
                trailers.add(FileTrailer.builder()
                        .becsFile(becsFile)
                        .bsbFiller(pt.getBsbFiller())
                        .netTotalAmount(pt.getNetTotalAmount())
                        .creditTotalAmount(pt.getCreditTotalAmount())
                        .debitTotalAmount(pt.getDebitTotalAmount())
                        .recordCount(pt.getRecordCount())
                        .build());
            }
            becsFile.setFileTrailers(trailers);

            // Persist headers/trailers now so payment records can reference them
            becsFileRepo.save(becsFile);

            // ---- 5. Write the debulked file (last header + all details + last trailer) ----
            List<ParsedPayment> payments = parsed.getPayments();
            ParsedHeader  lastHeader  = headers.isEmpty()  ? null : parsed.getHeaders().get(parsed.getHeaders().size() - 1);
            ParsedTrailer lastTrailer = trailers.isEmpty() ? null : parsed.getTrailers().get(parsed.getTrailers().size() - 1);
            Path outPath = fileStorage.writeDebulkedFile(fileName, fileType, lastHeader, payments, lastTrailer);
            String outStr = outPath.toAbsolutePath().toString();
            String outName = outPath.getFileName().toString();
            switch (fileType) {
                case RET -> { becsFile.setRetOutputFilePath(outStr); becsFile.setRetOutFileName(outName); }
                case NDE -> { becsFile.setNdeOutputFilePath(outStr); becsFile.setNdeOutFileName(outName); }
                case BPY -> { becsFile.setBpyOutputFilePath(outStr); becsFile.setBpyOutFileName(outName); }
            }

            List<PaymentRecord> records = new ArrayList<>();
            for (ParsedPayment p : payments) {
                records.add(PaymentRecord.builder()
                        .becsFile(becsFile)
                        .fileHeader(headerForPayment(p, parsed.getHeaders(), headers))
                        .fileTrailer(trailerForPayment(p, parsed.getTrailers(), trailers))
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
            becsFile.setStatus(BecsFileStatus.COMPLETED);
            becsFile.setProcessedAt(LocalDateTime.now());
            becsFile.setRecordCount(records.size());
            switch (fileType) {
                case RET -> becsFile.setRetRecordCount(records.size());
                case NDE -> becsFile.setNdeRecordCount(records.size());
                case BPY -> becsFile.setBpyRecordCount(records.size());
            }
            becsFile.setArchivedPath(archivePath.toAbsolutePath().toString());
            becsFileRepo.save(becsFile);

            log.info("Completed processing {}: {} payment records",
                    fileName, records.size());

        } catch (Exception e) {
            log.error("Failed to process {}: {}", fileName, e.getMessage(), e);
            becsFile.setStatus(BecsFileStatus.FAILED);
            becsFile.setErrorMessage(e.getMessage());
            becsFileRepo.save(becsFile);

            try { fileStorage.moveToError(inboxPath); }
            catch (Exception ex) { log.error("Cannot move file to error dir: {}", ex.getMessage()); }
        }

        return becsFile;
    }

    /**
     * The reel header a payment sits under: the last header that appears
     * before the payment in the source file (matched by record position).
     */
    private FileHeader headerForPayment(ParsedPayment p,
                                        List<ParsedHeader> parsedHeaders,
                                        List<FileHeader> headers) {
        FileHeader match = null;
        for (int i = 0; i < parsedHeaders.size(); i++) {
            if (parsedHeaders.get(i).getLineNumber() < p.getLineNumber()) {
                match = headers.get(i);
            }
        }
        return match;
    }

    /**
     * The reel trailer a payment sits under: the first trailer that appears
     * after the payment in the source file (matched by record position).
     */
    private FileTrailer trailerForPayment(ParsedPayment p,
                                          List<ParsedTrailer> parsedTrailers,
                                          List<FileTrailer> trailers) {
        for (int i = 0; i < parsedTrailers.size(); i++) {
            if (parsedTrailers.get(i).getLineNumber() > p.getLineNumber()) {
                return trailers.get(i);
            }
        }
        return null;
    }
}
