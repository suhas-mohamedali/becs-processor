package com.becs.processor.service;

import com.becs.processor.config.BecsProperties;
import com.becs.processor.dto.ParsedPayment;
import com.becs.processor.model.BsbSequence;
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
        when(sequenceRepo.findById("12345")).thenReturn(Optional.empty());

        Path out = fileStorage.writeDebulkedFile("12345.bpy.001", null, List.<ParsedPayment>of(), null);

        assertThat(out.getFileName().toString()).isEqualTo("12345.bpy.001");
        verify(sequenceRepo).save(argThat(s -> s.getBsbNumber().equals("12345") && s.getLastSequence() == 1));
    }

    @Test
    void incrementsFromExistingSequence() throws IOException {
        when(sequenceRepo.findById("12345")).thenReturn(Optional.of(new BsbSequence("12345", 67)));

        Path out = fileStorage.writeDebulkedFile("12345.bpy.044", null, List.<ParsedPayment>of(), null);

        assertThat(out.getFileName().toString()).isEqualTo("12345.bpy.068");
    }

    @Test
    void wrapsBackToOneAfter999() throws IOException {
        when(sequenceRepo.findById("12345")).thenReturn(Optional.of(new BsbSequence("12345", 999)));

        Path out = fileStorage.writeDebulkedFile("12345.bpy.500", null, List.<ParsedPayment>of(), null);

        assertThat(out.getFileName().toString()).isEqualTo("12345.bpy.001");
    }
}
