package com.becs.processor.parser;

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

    /**
     * Sample BECS DE file (header + 2 detail lines + trailer), each exactly 120 chars.
     */
    private static final List<String> SAMPLE_LINES = List.of(
        // Type 0 – Header
        pad("001ANZ       Payroll File              062000    WAGES         260625", 120),
        // Type 1 – Credit (txn 50)
        pad("1062-001 123456789 5000001234John Smith          WAGES JUNE        062-001 987654321Acme Corp       00000000", 120),
        // Type 1 – Debit (txn 13)
        pad("1063-000 000112233 1300005678Jane Doe            WAGES JUNE        063-000 111222333Acme Corp       00000000", 120),
        // Type 7 – Trailer
        pad("7999-999            000000006912000000005678000000001234      000002", 120)
    );

    @Test
    void parsesHeaderCorrectly() throws IOException {
        Path bpy = writeFile(SAMPLE_LINES);
        ParsedBpyFile result = parser.parse(bpy);

        assertThat(result.getHeader()).isNotNull();
        assertThat(result.getHeader().getFinancialInstitution()).isEqualTo("ANZ");
        assertThat(result.getHeader().getUserId()).isEqualTo("062000");
    }

    @Test
    void parsesDetailRecords() throws IOException {
        Path bpy = writeFile(SAMPLE_LINES);
        ParsedBpyFile result = parser.parse(bpy);

        assertThat(result.getPayments()).hasSize(2);
        assertThat(result.getPayments().get(0).getTransactionCode()).isEqualTo("50");
        assertThat(result.getPayments().get(1).getTransactionCode()).isEqualTo("13");
    }

    @Test
    void parsesTrailerRecordCount() throws IOException {
        Path bpy = writeFile(SAMPLE_LINES);
        ParsedBpyFile result = parser.parse(bpy);

        assertThat(result.getTrailer()).isNotNull();
        assertThat(result.getTrailer().getRecordCount()).isEqualTo(2);
    }

    @Test
    void handlesEmptyFile() throws IOException {
        Path bpy = writeFile(List.of());
        ParsedBpyFile result = parser.parse(bpy);

        assertThat(result.getPayments()).isEmpty();
        assertThat(result.getHeader()).isNull();
    }

    // ---- helpers ----

    private Path writeFile(List<String> lines) throws IOException {
        Path file = tempDir.resolve("test.bpy");
        Files.write(file, lines, StandardCharsets.UTF_8);
        return file;
    }

    private static String pad(String s, int len) {
        if (s.length() >= len) return s.substring(0, len);
        return String.format("%-" + len + "s", s);
    }
}
