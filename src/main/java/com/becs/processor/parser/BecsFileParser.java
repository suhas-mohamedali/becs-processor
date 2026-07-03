package com.becs.processor.parser;

import com.becs.processor.dto.ParsedBecsFile;
import com.becs.processor.dto.ParsedHeader;
import com.becs.processor.dto.ParsedPayment;
import com.becs.processor.dto.ParsedTrailer;
import com.becs.processor.model.FileType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a BECS Direct Entry (DE) file. BPY (payment), RET (return) and NDE
 * bundles all share the same 120-char record layouts; they differ only in
 * framing:
 *  - BPY/RET: one record per line, preceded by a service/preamble line
 *    (always skipped) and optionally a title line.
 *  - NDE: no preamble; records are concatenated as 120-char blocks on a
 *    single line, padded out with all-'9' filler blocks, followed by an
 *    "End-Of-File" text line.
 *
 * Record types:
 *  Type 0    – File Header
 *  Type 1    – Detail record (payment)
 *  Type 2, 3 – Detail record (return)
 *  Type 7    – File Control (trailer)
 *  Type 9    – All-'9' filler block (NDE padding, ignored)
 *
 * Field positions follow the APCA/BECS DE standard.
 */
@Slf4j
@Component
public class BecsFileParser {

    private static final DateTimeFormatter BECS_DATE = DateTimeFormatter.ofPattern("ddMMyy");
    private static final String END_OF_FILE_MARKER   = "End-Of-File";

    public ParsedBecsFile parse(Path filePath, FileType fileType) throws IOException {
        List<ParsedPayment> payments = new ArrayList<>();
        List<ParsedHeader>  headers  = new ArrayList<>();
        List<ParsedTrailer> trailers = new ArrayList<>();

        List<String> records = fileType == FileType.NDE
                ? readNdeRecords(filePath)
                : readLineRecords(filePath);

        int recordNo = fileType == FileType.NDE ? 0 : 1; // line-based formats skip the preamble line

        for (String record : records) {
            recordNo++;

            // Pad or trim to 120 chars
            if (record.length() < 120) {
                record = String.format("%-120s", record);
            }

            char type = record.charAt(0);

            switch (type) {
                case '0' -> { ParsedHeader h = parseHeader(record, recordNo); if (h != null) headers.add(h); }
                case '1', '2', '3' -> payments.add(parseDetail(record, recordNo, type));
                case '7' -> trailers.add(parseTrailer(record, recordNo));
                case '9' -> { /* NDE all-'9' filler block — ignore */ }
                default  -> log.warn("Unknown record type '{}' at record {}", type, recordNo);
            }
        }

        log.info("Parsed file {}: headers={}, records={}, trailers={}",
                filePath.getFileName(),
                headers.size(),
                payments.size(),
                trailers.size());

        return ParsedBecsFile.builder()
                .headers(headers)
                .payments(payments)
                .trailers(trailers)
                .build();
    }

    /** BPY/RET framing: one record per line, always skipping the first (preamble) line. */
    private List<String> readLineRecords(Path filePath) throws IOException {
        List<String> records = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.ISO_8859_1)) {
            reader.readLine(); // always skip the first line of the file
            String line;
            while ((line = reader.readLine()) != null) {
                records.add(line);
            }
        }
        return records;
    }

    /**
     * NDE framing: records are concatenated as 120-char blocks with no line
     * separators. Blank lines and the trailing "End-Of-File" marker are
     * skipped; all-'9' filler blocks are passed through as type-9 records
     * and dropped by the dispatch loop.
     */
    private List<String> readNdeRecords(Path filePath) throws IOException {
        List<String> records = new ArrayList<>();
        for (String line : Files.readAllLines(filePath, StandardCharsets.ISO_8859_1)) {
            if (line.isBlank() || line.strip().equalsIgnoreCase(END_OF_FILE_MARKER)) {
                continue;
            }
            for (int offset = 0; offset < line.length(); offset += 120) {
                records.add(line.substring(offset, Math.min(offset + 120, line.length())));
            }
        }
        return records;
    }

    // ---------------------------------------------------------------
    // Type 0 – File Header (120 chars)
    // Pos  1      : Record type "0"
    // Pos  2-18   : Blank
    // Pos  19-20  : Reel sequence number
    // Pos  21-23  : Financial institution
    // Pos  24-30  : Blank
    // Pos  31-56  : User preferred spec (left-justified, space-filled)
    // Pos  57-62  : User ID
    // Pos  63-74  : Description
    // Pos  75-80  : Processing date (DDMMYY)
    // Pos  81-120 : Spare
    // ---------------------------------------------------------------
    private ParsedHeader parseHeader(String line, int lineNo) {
        try {
            String reelSeq      = substring(line, 18, 20).trim();
            String institution  = substring(line, 20, 23).trim();
            String userSpec     = substring(line, 30, 56).trim();
            String userId       = substring(line, 56, 62).trim();
            String description  = substring(line, 62, 74).trim();
            String dateStr      = substring(line, 74, 80).trim();

            LocalDate processingDate = null;
            if (!dateStr.isBlank()) {
                try { processingDate = LocalDate.parse(dateStr, BECS_DATE); }
                catch (Exception e) { log.warn("Cannot parse header date '{}' at line {}", dateStr, lineNo); }
            }

            return ParsedHeader.builder()
                    .reelSequenceNumber(reelSeq)
                    .financialInstitution(institution)
                    .userPreferredSpec(userSpec)
                    .userId(userId)
                    .description(description)
                    .processingDate(processingDate)
                    .lineNumber(lineNo)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse header at line {}: {}", lineNo, e.getMessage());
            return null;
        }
    }

    // ---------------------------------------------------------------
    // Type 1/2/3 – Detail Record (120 chars). Type 1 is a payment (BPY
    // files), types 2 and 3 are returns (RET files); all share this exact
    // field layout.
    // Pos  1      : Record type "1", "2" or "3"
    // Pos  2-8    : BSB (NNN-NNN)
    // Pos  9-17   : Account number
    // Pos  18     : Withholding tax indicator
    // Pos  19-20  : Transaction code
    // Pos  21-30  : Amount ($$$$$$$cc, no decimal, in cents)
    // Pos  31-62  : Account name (32 chars)
    // Pos  63-80  : Lodgement reference (18 chars)
    // Pos  81-87  : Trace BSB
    // Pos  88-96  : Trace account number
    // Pos  97-112 : Remitter name (16 chars)
    // Pos 113-120 : Withholding tax amount (cents)
    // ---------------------------------------------------------------
    private ParsedPayment parseDetail(String line, int lineNo, char type) {
        String bsb           = substring(line, 1, 8).trim();
        String accountNo     = substring(line, 8, 17).trim();
        String indicator     = substring(line, 17, 18).trim();
        String txnCode       = substring(line, 18, 20).trim();
        String amountStr     = substring(line, 20, 30).trim();
        String accountName   = substring(line, 30, 62).trim();
        String lodgementRef  = substring(line, 62, 80).trim();
        String traceBsb      = substring(line, 80, 87).trim();
        String traceAccount  = substring(line, 87, 96).trim();
        String remitterName  = substring(line, 96, 112).trim();
        String taxStr        = substring(line, 112, 120).trim();

        Long amount = parseLong(amountStr, lineNo, "amount");
        Long tax    = parseLong(taxStr,    lineNo, "withholding tax");

        return ParsedPayment.builder()
                .bsbNumber(bsb)
                .accountNumber(accountNo)
                .indicator(indicator)
                .transactionCode(txnCode)
                .amountCents(amount)
                .accountName(accountName)
                .lodgementReference(lodgementRef)
                .traceBsb(traceBsb)
                .traceAccount(traceAccount)
                .remitterName(remitterName)
                .withholdingTax(tax)
                .lineNumber(lineNo)
                .recordType(String.valueOf(type))
                .build();
    }

    // ---------------------------------------------------------------
    // Type 7 – File Trailer (120 chars)
    // Pos  1      : Record type "7"
    // Pos  2-8    : BSB filler "999-999"
    // Pos  9-20   : Spare
    // Pos  21-30  : Net total amount (cents)
    // Pos  31-40  : Credit total amount (cents)
    // Pos  41-50  : Debit total amount (cents)
    // Pos  51-74  : Spare
    // Pos  75-80  : Record count
    // Pos  81-120 : Spare
    // ---------------------------------------------------------------
    private ParsedTrailer parseTrailer(String line, int lineNo) {
        String bsbFiller = substring(line, 1, 8).trim();
        String netStr    = substring(line, 20, 30).trim();
        String creditStr = substring(line, 30, 40).trim();
        String debitStr  = substring(line, 40, 50).trim();
        String countStr  = substring(line, 74, 80).trim();

        return ParsedTrailer.builder()
                .bsbFiller(bsbFiller)
                .netTotalAmount(parseLong(netStr,    lineNo, "net total"))
                .creditTotalAmount(parseLong(creditStr, lineNo, "credit total"))
                .debitTotalAmount(parseLong(debitStr,  lineNo, "debit total"))
                .recordCount(parseInteger(countStr, lineNo, "record count"))
                .lineNumber(lineNo)
                .build();
    }

    // ---- helpers ----

    /** Safe substring; returns spaces if out of range */
    private String substring(String s, int start, int end) {
        if (start >= s.length()) return "";
        int e = Math.min(end, s.length());
        return s.substring(start, e);
    }

    private Long parseLong(String s, int lineNo, String field) {
        if (s == null || s.isBlank()) return 0L;
        try { return Long.parseLong(s.replaceAll("\\s", "")); }
        catch (NumberFormatException e) {
            log.warn("Cannot parse {} '{}' at line {}", field, s, lineNo);
            return 0L;
        }
    }

    private Integer parseInteger(String s, int lineNo, String field) {
        Long l = parseLong(s, lineNo, field);
        return l == null ? null : l.intValue();
    }
}
