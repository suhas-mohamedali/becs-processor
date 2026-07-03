package com.becs.processor;

import com.becs.processor.dto.ParsedBecsFile;
import com.becs.processor.model.FileType;
import com.becs.processor.parser.BecsFileParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class BecsFileParserTest {

    private final BecsFileParser parser = new BecsFileParser();

    @TempDir
    Path tempDir;

    // A real BPY file starts with a leading service/preamble line that isn't
    // part of the BECS DE record layout; the parser always skips it.
    private static final String PREAMBLE = "svc_palftp";

    // Type 0 – Header: '0' + blank(17) + reelSeq(2) + institution(3) + blank(7)
    //                  + userSpec(26) + userId(6) + description(12) + date(6) + blank(40)
    private static String header(String institution, String userId) {
        return "0" + field("", 17) + field("01", 2) + field(institution, 3) + field("", 7)
                + field("Payroll File", 26) + field(userId, 6) + field("WAGES", 12)
                + field("260625", 6) + field("", 40);
    }

    private static final String HEADER = header("ANZ", "062000");

    // Type 1 – Detail: '1' + bsb(7) + acct(9) + indicator(1) + txnCode(2) + amount(10)
    //                  + name(32) + lodgement(18) + traceBsb(7) + traceAcct(9) + remitter(16) + tax(8)
    private static final String DETAIL_CREDIT =
            "1" + field("062-001", 7) + field("123456789", 9) + field("", 1) + field("50", 2)
                + amount(100000) + field("John Smith", 32) + field("WAGES JUNE", 18)
                + field("062-001", 7) + field("987654321", 9) + field("Acme Corp", 16) + amount8(0);

    private static final String DETAIL_DEBIT =
            "1" + field("063-000", 7) + field("000112233", 9) + field("", 1) + field("13", 2)
                + amount(50000) + field("Jane Doe", 32) + field("WAGES JUNE", 18)
                + field("063-000", 7) + field("111222333", 9) + field("Acme Corp", 16) + amount8(0);

    // Type 7 – Trailer: '7' + bsbFiller(7) + blank(12) + net(10) + credit(10) + debit(10)
    //                   + blank(24) + recordCount(6) + blank(40)
    private static String trailer(int recordCount) {
        return "7" + field("999-999", 7) + field("", 12)
                + amount(50000) + amount(100000) + amount(50000)
                + field("", 24) + String.format("%06d", recordCount) + field("", 40);
    }

    private static final String TRAILER = trailer(2);

    private static final List<String> SAMPLE_LINES =
            List.of(PREAMBLE, HEADER, DETAIL_CREDIT, DETAIL_DEBIT, TRAILER);

    @Test
    void parsesHeaderCorrectly() throws IOException {
        Path bpy = writeFile(SAMPLE_LINES);
        ParsedBecsFile result = parser.parse(bpy, FileType.BPY);

        assertThat(result.getHeaders()).hasSize(1);
        assertThat(result.getHeaders().get(0).getFinancialInstitution()).isEqualTo("ANZ");
        assertThat(result.getHeaders().get(0).getUserId()).isEqualTo("062000");
    }

    @Test
    void parsesDetailRecords() throws IOException {
        Path bpy = writeFile(SAMPLE_LINES);
        ParsedBecsFile result = parser.parse(bpy, FileType.BPY);

        assertThat(result.getPayments()).hasSize(2);
        assertThat(result.getPayments().get(0).getTransactionCode()).isEqualTo("50");
        assertThat(result.getPayments().get(0).getAmountCents()).isEqualTo(100000L);
        assertThat(result.getPayments().get(1).getTransactionCode()).isEqualTo("13");
        assertThat(result.getPayments().get(1).getAmountCents()).isEqualTo(50000L);
    }

    @Test
    void parsesTrailerRecordCount() throws IOException {
        Path bpy = writeFile(SAMPLE_LINES);
        ParsedBecsFile result = parser.parse(bpy, FileType.BPY);

        assertThat(result.getTrailers()).hasSize(1);
        assertThat(result.getTrailers().get(0).getRecordCount()).isEqualTo(2);
    }

    @Test
    void skipsFirstLineOfFile() throws IOException {
        Path bpy = writeFile(SAMPLE_LINES);
        ParsedBecsFile result = parser.parse(bpy, FileType.BPY);

        // PREAMBLE would parse as an unknown record type if it weren't skipped;
        // the header must still be the one at index 1, not misread from PREAMBLE.
        assertThat(result.getHeaders().get(0).getFinancialInstitution()).isEqualTo("ANZ");
    }

    @Test
    void capturesEveryHeaderAndTrailerAcrossMultipleReels() throws IOException {
        // A real BPY file can contain multiple reels, each with its own
        // header and trailer; every one of them must be captured, not just
        // the last (which is all the parser used to keep).
        List<String> multiReelLines = List.of(
                PREAMBLE,
                header("ANZ", "062000"), DETAIL_CREDIT, trailer(1),
                header("CBA", "099000"), DETAIL_DEBIT, trailer(1)
        );
        Path bpy = writeFile(multiReelLines);
        ParsedBecsFile result = parser.parse(bpy, FileType.BPY);

        assertThat(result.getHeaders()).hasSize(2);
        assertThat(result.getHeaders().get(0).getFinancialInstitution()).isEqualTo("ANZ");
        assertThat(result.getHeaders().get(1).getFinancialInstitution()).isEqualTo("CBA");

        assertThat(result.getTrailers()).hasSize(2);

        assertThat(result.getPayments()).hasSize(2);
    }

    @Test
    void parsesReturnDetailRecordsAsType2() throws IOException {
        assertParsesReturnDetailAs('2');
    }

    @Test
    void parsesReturnDetailRecordsAsType3() throws IOException {
        assertParsesReturnDetailAs('3');
    }

    private void assertParsesReturnDetailAs(char recordType) throws IOException {
        // RET files reuse the exact same 120-char detail layout as BPY files,
        // just with record type '2' or '3' instead of '1'.
        String returnDetail =
                recordType + field("062-001", 7) + field("123456789", 9) + field("6", 1) + field("50", 2)
                    + amount(10600) + field("SUMIT DONOTTOUCH", 32) + field("", 18)
                    + field("671-998", 7) + field("104081498", 9) + field("CBA", 16) + amount8(30000112);

        List<String> lines = List.of(PREAMBLE, "RETURNS", HEADER, returnDetail, trailer(1));
        Path ret = writeFile(lines);
        ParsedBecsFile result = parser.parse(ret, FileType.RET);

        assertThat(result.getPayments()).hasSize(1);
        assertThat(result.getPayments().get(0).getRecordType()).isEqualTo(String.valueOf(recordType));
        assertThat(result.getPayments().get(0).getAccountName().trim()).isEqualTo("SUMIT DONOTTOUCH");
    }

    @Test
    void parsesNdeFilesAsConcatenatedBlocks() throws IOException {
        // NDE files have no preamble line; records are concatenated as
        // 120-char blocks on a single line, padded with all-'9' filler
        // blocks and followed by an End-Of-File marker line.
        String blocks = header("CRU", "000155")
                + DETAIL_CREDIT
                + trailer(1)
                + header("CRU", "000155")
                + DETAIL_DEBIT
                + trailer(1)
                + "9".repeat(240);
        List<String> lines = List.of(blocks, "", "End-Of-File");
        Path nde = writeFile(lines);
        ParsedBecsFile result = parser.parse(nde, FileType.NDE);

        assertThat(result.getHeaders()).hasSize(2);
        assertThat(result.getHeaders().get(0).getFinancialInstitution()).isEqualTo("CRU");
        assertThat(result.getHeaders().get(0).getUserId()).isEqualTo("000155");
        assertThat(result.getPayments()).hasSize(2);
        assertThat(result.getTrailers()).hasSize(2);
        assertThat(result.getTrailers().get(0).getRecordCount()).isEqualTo(1);
    }

    @Test
    void handlesEmptyFile() throws IOException {
        Path bpy = writeFile(List.of());
        ParsedBecsFile result = parser.parse(bpy, FileType.BPY);

        assertThat(result.getPayments()).isEmpty();
        assertThat(result.getHeaders()).isEmpty();
    }

    // ---- helpers ----

    private Path writeFile(List<String> lines) throws IOException {
        Path file = tempDir.resolve("test.bpy");
        Files.write(file, lines, StandardCharsets.UTF_8);
        return file;
    }

    private static String field(String s, int len) {
        if (s.length() >= len) return s.substring(0, len);
        return String.format("%-" + len + "s", s);
    }

    private static String amount(long cents) {
        return String.format("%010d", cents);
    }

    private static String amount8(long cents) {
        return String.format("%08d", cents);
    }
}
