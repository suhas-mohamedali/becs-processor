package com.becs.processor.service;

import com.becs.processor.config.BecsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Polls the inbox directory for new .bpy files and triggers processing.
 * Interval is driven by becs.poll-interval-ms (default 30 s).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InboxPollerService {

    private final BecsProperties      props;
    private final BpyProcessingService processingService;

    @Scheduled(fixedDelayString = "${becs.poll-interval-ms:30000}",
               initialDelayString = "${becs.poll-interval-ms:30000}")
    public void poll() {
        Path inbox = props.inbox();
        log.debug("Polling inbox: {}", inbox);

        List<Path> bpyFiles;
        try (Stream<Path> stream = Files.list(inbox)) {
            bpyFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".bpy"))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Cannot read inbox directory {}: {}", inbox, e.getMessage());
            return;
        }

        if (bpyFiles.isEmpty()) {
            log.debug("No BPY files found in inbox");
            return;
        }

        log.info("Found {} BPY file(s) in inbox", bpyFiles.size());
        for (Path file : bpyFiles) {
            try {
                processingService.process(file);
            } catch (Exception e) {
                log.error("Unhandled error processing {}: {}", file.getFileName(), e.getMessage(), e);
            }
        }
    }
}
