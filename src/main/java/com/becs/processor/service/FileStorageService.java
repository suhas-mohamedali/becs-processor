package com.becs.processor.service;

import com.becs.processor.config.BecsProperties;
import com.becs.processor.dto.ParsedHeader;
import com.becs.processor.dto.ParsedPayment;
import com.becs.processor.dto.ParsedTrailer;
import com.becs.processor.model.BsbSequence;
import com.becs.processor.model.FileType;
import com.becs.processor.repository.BsbSequenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Handles all file I/O:
 *  - Writing debulked individual payment files to the output directory
 *  - Archiving processed source BPY files
 *  - Moving failed files to the error directory
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final DateTimeFormatter DIR_DATE   = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final DateTimeFormatter BECS_DATE  = DateTimeFormatter.ofPattern("ddMMyy");

    private final BecsProperties         props;
    private final BsbSequenceRepository  sequenceRepo;

    /**
     * Write the full debulked BECS DE file (header + all detail records + trailer)
     * as a single file under:
     *   output/<yyyy>/<MM>/<dd>/<bsb_number>.<bpy|ret>.<nnn>
     *
     * nnn is the next value (001-999, wrapping back to 001 after 999) of the
     * per-BSB, per-file-type counter tracked in becs_bsb_sequence.
     *
     * @param fileType BPY or RET — determines the output extension and
     *                 which sequence counter bucket is used.
     * Returns the path of the written file.
     */
    public Path writeDebulkedFile(String inputFileName,
                                  FileType fileType,
                                  ParsedHeader header,
                                  List<ParsedPayment> payments,
                                  ParsedTrailer trailer) throws IOException {
        Path dateDir = props.output().resolve(LocalDate.now().format(DIR_DATE));
        Files.createDirectories(dateDir);

        String bsbNumber = extractBsbNumber(inputFileName);
        int    sequence  = nextSequence(bsbNumber, fileType);
        String outName   = bsbNumber + "." + fileType.name().toLowerCase() + "." + String.format("%03d", sequence);
        Path   outPath   = dateDir.resolve(outName);

        try (BufferedWriter w = Files.newBufferedWriter(outPath, StandardCharsets.ISO_8859_1)) {
            if (header != null) {
                w.write(formatHeaderLine(header));
                w.newLine();
            }
            for (ParsedPayment p : payments) {
                w.write(formatDetailLine(p));
                w.newLine();
            }
            if (trailer != null) {
                w.write(formatTrailerLine(trailer));
                w.newLine();
            }
        }

        log.info("Debulked file written: {} ({} records)", outPath, payments.size());
        return outPath;
    }

    /**
     * Move the original BPY file to the archive directory once processing completes.
     * Returns the archive path.
     */
    public Path archiveSourceFile(Path sourcePath) throws IOException {
        Path dateDir = props.archive().resolve(LocalDate.now().format(DIR_DATE));
        Files.createDirectories(dateDir);

        Path destination = dateDir.resolve(sourcePath.getFileName());
        Files.move(sourcePath, destination, StandardCopyOption.REPLACE_EXISTING);
        log.info("Archived source file: {} → {}", sourcePath, destination);
        return destination;
    }

    /**
     * Move a file that could not be processed to the error directory.
     */
    public Path moveToError(Path sourcePath) throws IOException {
        Path dateDir = props.error().resolve(LocalDate.now().format(DIR_DATE));
        Files.createDirectories(dateDir);

        Path destination = dateDir.resolve(sourcePath.getFileName());
        Files.move(sourcePath, destination, StandardCopyOption.REPLACE_EXISTING);
        log.warn("Moved failed file to error dir: {}", destination);
        return destination;
    }

    // ------------------------------------------------------------------
    // Format the Type-0 BECS DE file header line (120 chars)
    // ------------------------------------------------------------------
    private String formatHeaderLine(ParsedHeader h) {
        String dateStr = h.getProcessingDate() == null ? "" : h.getProcessingDate().format(BECS_DATE);
        return "0" +
               pad("",                          17) + // spare
               pad(h.getReelSequenceNumber(),   2)  +
               pad(h.getFinancialInstitution(), 3)  +
               pad("",                          7)  + // spare
               pad(h.getUserPreferredSpec(),    26) +
               pad(h.getUserId(),               6)  +
               pad(h.getDescription(),          12) +
               pad(dateStr,                     6)  +
               pad("",                          40);  // spare
    }

    // ------------------------------------------------------------------
    // Format the Type-7 BECS DE file trailer line (120 chars)
    // ------------------------------------------------------------------
    private String formatTrailerLine(ParsedTrailer t) {
        return "7" +
               pad(t.getBsbFiller(), 7) +
               pad("", 12) + // spare
               padLeft(String.valueOf(t.getNetTotalAmount()    == null ? 0 : t.getNetTotalAmount()),    10) +
               padLeft(String.valueOf(t.getCreditTotalAmount() == null ? 0 : t.getCreditTotalAmount()), 10) +
               padLeft(String.valueOf(t.getDebitTotalAmount()  == null ? 0 : t.getDebitTotalAmount()),  10) +
               pad("", 24) + // spare
               padLeft(String.valueOf(t.getRecordCount() == null ? 0 : t.getRecordCount()), 6) +
               pad("", 40); // spare
    }

    // ------------------------------------------------------------------
    // Format a single Type-1 BECS DE detail line (120 chars)
    // ------------------------------------------------------------------
    private String formatDetailLine(ParsedPayment p) {
        return p.getRecordType() +
               pad(p.getBsbNumber(),        7)  +
               pad(p.getAccountNumber(),    9)  +
               pad(p.getIndicator(),        1)  +
               pad(p.getTransactionCode(),  2)  +
               padLeft(String.valueOf(p.getAmountCents() == null ? 0 : p.getAmountCents()), 10) +
               pad(p.getAccountName(),      32) +
               pad(p.getLodgementReference(), 18) +
               pad(p.getTraceBsb(),         7)  +
               pad(p.getTraceAccount(),     9)  +
               pad(p.getRemitterName(),     16) +
               padLeft(String.valueOf(p.getWithholdingTax() == null ? 0 : p.getWithholdingTax()), 8);
    }

    private String pad(String s, int len) {
        if (s == null) s = "";
        if (s.length() >= len) return s.substring(0, len);
        return String.format("%-" + len + "s", s);
    }

    private String padLeft(String s, int len) {
        if (s == null) s = "0";
        if (s.length() >= len) return s.substring(0, len);
        return String.format("%" + len + "s", s).replace(' ', '0');
    }

    /** Extracts the BSB number from an input file named {bsb_number}.<bpy|ret>.nnn */
    private String extractBsbNumber(String inputFileName) {
        int dot = inputFileName.indexOf('.');
        return dot < 0 ? inputFileName : inputFileName.substring(0, dot);
    }

    /**
     * Next value (001-999, wrapping back to 001 after 999) of the output
     * sequence counter for this BSB number + file type, tracked in
     * becs_bsb_sequence.
     */
    private int nextSequence(String bsbNumber, FileType fileType) {
        String key = bsbNumber + ":" + fileType.name();
        BsbSequence seq = sequenceRepo.findById(key)
                .orElseGet(() -> new BsbSequence(key, 0));

        int next = seq.getLastSequence() >= 999 ? 1 : seq.getLastSequence() + 1;
        seq.setLastSequence(next);
        sequenceRepo.save(seq);
        return next;
    }
}
