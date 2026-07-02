package com.becs.processor.service;

import com.becs.processor.config.BecsProperties;
import com.becs.processor.dto.ParsedPayment;
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

    private static final DateTimeFormatter DIR_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final BecsProperties props;

    /**
     * Write a set of payment records as a debulked BECS DE file under:
     *   output/<yyyy>/<MM>/<dd>/<bpyFileName>_<bsb>.de
     *
     * Returns the path of the written file.
     */
    public Path writeDebulkedFile(String bpyFileName,
                                  String bsb,
                                  List<ParsedPayment> payments) throws IOException {
        Path dateDir = props.output().resolve(LocalDate.now().format(DIR_DATE));
        Files.createDirectories(dateDir);

        String safeBsb = bsb.replace("-", "");
        String outName = stripExtension(bpyFileName) + "_" + safeBsb + ".de";
        Path   outPath = dateDir.resolve(outName);

        try (BufferedWriter w = Files.newBufferedWriter(outPath, StandardCharsets.UTF_8)) {
            for (ParsedPayment p : payments) {
                w.write(formatDetailLine(p));
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
    // Format a single Type-1 BECS DE detail line (120 chars)
    // ------------------------------------------------------------------
    private String formatDetailLine(ParsedPayment p) {
        return "1" +
               pad(p.getBsbNumber(),        7)  +
               pad(p.getAccountNumber(),    9)  +
               pad(p.getIndicator(),        1)  +
               pad(p.getTransactionCode(),  2)  +
               padLeft(String.valueOf(p.getAmountCents() == null ? 0 : p.getAmountCents()), 9) +
               pad(p.getAccountName(),      32) +
               pad(p.getLodgementReference(), 18) +
               pad(p.getTraceBsb(),         7)  +
               pad(p.getTraceAccount(),     9)  +
               pad(p.getRemitterName(),     16) +
               padLeft(String.valueOf(p.getWithholdingTax() == null ? 0 : p.getWithholdingTax()), 9);
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

    private String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }
}
