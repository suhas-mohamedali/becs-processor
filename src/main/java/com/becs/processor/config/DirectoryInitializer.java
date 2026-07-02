package com.becs.processor.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;

@Slf4j
@Component
@RequiredArgsConstructor
public class DirectoryInitializer implements ApplicationRunner {

    private final BecsProperties props;

    @Override
    public void run(ApplicationArguments args) throws IOException {
        createIfMissing(props.inbox());
        createIfMissing(props.archive());
        createIfMissing(props.output());
        createIfMissing(props.error());
        log.info("BECS directories ready: inbox={}, archive={}, output={}, error={}",
                props.getInboxDir(), props.getArchiveDir(),
                props.getOutputDir(), props.getErrorDir());
    }

    private void createIfMissing(java.nio.file.Path path) throws IOException {
        if (!Files.exists(path)) {
            Files.createDirectories(path);
            log.info("Created directory: {}", path);
        }
    }
}
