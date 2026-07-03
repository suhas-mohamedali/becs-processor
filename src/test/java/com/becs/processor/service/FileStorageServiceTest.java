package com.becs.processor.service;

import com.becs.processor.config.BecsProperties;
import com.becs.processor.dto.ParsedPayment;
import com.becs.processor.model.BsbSequence;
import com.becs.processor.model.FileType;
import com.becs.processor.repository.BsbSequenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    @Mock
    private BsbSequenceRepository sequenceRepo;

    @TempDir
    Path tempDir;

    private FileStorageService fileStorage;

    @BeforeEach
    void setUp() {
        BecsProperties props = new BecsProperties();
        props.setOutputDir(tempDir.resolve("output").toString());
        fileStorage = new FileStorageService(props, sequenceRepo);
    }

    @Test
    void startsAtOneWhenNoExistingSequence() throws IOException {
        when(sequenceRepo.findById("12345:BPY")).thenReturn(Optional.empty());

        Path out = fileStorage.writeDebulkedFile("12345.bpy.001", FileType.BPY, null, List.<ParsedPayment>of(), null);

        assertThat(out.getFileName().toString()).isEqualTo("12345.bpy.001");
        verify(sequenceRepo).save(argThat(s -> s.getSequenceKey().equals("12345:BPY") && s.getLastSequence() == 1));
    }

    @Test
    void incrementsFromExistingSequence() throws IOException {
        when(sequenceRepo.findById("12345:BPY")).thenReturn(Optional.of(new BsbSequence("12345:BPY", 67)));

        Path out = fileStorage.writeDebulkedFile("12345.bpy.044", FileType.BPY, null, List.<ParsedPayment>of(), null);

        assertThat(out.getFileName().toString()).isEqualTo("12345.bpy.068");
    }

    @Test
    void wrapsBackToOneAfter999() throws IOException {
        when(sequenceRepo.findById("12345:BPY")).thenReturn(Optional.of(new BsbSequence("12345:BPY", 999)));

        Path out = fileStorage.writeDebulkedFile("12345.bpy.500", FileType.BPY, null, List.<ParsedPayment>of(), null);

        assertThat(out.getFileName().toString()).isEqualTo("12345.bpy.001");
    }

    @Test
    void retAndBpyCountersAreIndependentForTheSameBsb() throws IOException {
        when(sequenceRepo.findById("12345:RET")).thenReturn(Optional.of(new BsbSequence("12345:RET", 5)));

        Path out = fileStorage.writeDebulkedFile("12345.ret.511", FileType.RET, null, List.<ParsedPayment>of(), null);

        // A BPY counter of 67 elsewhere must not affect this RET-specific bucket.
        assertThat(out.getFileName().toString()).isEqualTo("12345.ret.006");
        verify(sequenceRepo, never()).findById("12345:BPY");
    }
}
