package com.becs.processor;

import com.becs.processor.dto.ParsedBpyFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class BpyFileParserTest {

    private final BpyFileParser parser = new BpyFileParser();

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
        ParsedBpyFile result = parser.parse(bpy);

        assertThat(result.getHeaders()).hasSize(1);
        assertThat(result.getHeaders().get(0).getFinancialInstitution()).isEqualTo("ANZ");
        assertThat(result.getHeaders().get(0).getUserId()).isEqualTo("062000");
    }

    @Test
    void parsesDetailRecords() throws IOException {
        Path bpy = writeFile(SAMPLE_LINES);
        ParsedBpyFile result = parser.parse(bpy);

        assertThat(result.getPayments()).hasSize(2);
        assertThat(result.getPayments().get(0).getTransactionCode()).isEqualTo("50");
        assertThat(result.getPayments().get(0).getAmountCents()).isEqualTo(100000L);
        assertThat(result.getPayments().get(1).getTransactionCode()).isEqualTo("13");
        assertThat(result.getPayments().get(1).getAmountCents()).isEqualTo(50000L);
    }

    @Test
    void parsesTrailerRecordCount() throws IOException {
        Path bpy = writeFile(SAMPLE_LINES);
        ParsedBpyFile result = parser.parse(bpy);

        assertThat(result.getTrailers()).hasSize(1);
        assertThat(result.getTrailers().get(0).getRecordCount()).isEqualTo(2);
    }

    @Test
    void skipsFirstLineOfFile() throws IOException {
        Path bpy = writeFile(SAMPLE_LINES);
        ParsedBpyFile result = parser.parse(bpy);

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
        ParsedBpyFile result = parser.parse(bpy);

        assertThat(result.getHeaders()).hasSize(2);
        assertThat(result.getHeaders().get(0).getFinancialInstitution()).isEqualTo("ANZ");
        assertThat(result.getHeaders().get(1).getFinancialInstitution()).isEqualTo("CBA");

        assertThat(result.getTrailers()).hasSize(2);

        assertThat(result.getPayments()).hasSize(2);
    }

    @Test
    void handlesEmptyFile() throws IOException {
        Path bpy = writeFile(List.of());
        ParsedBpyFile result = parser.parse(bpy);

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
