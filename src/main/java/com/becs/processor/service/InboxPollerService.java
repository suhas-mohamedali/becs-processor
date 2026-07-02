package com.becs.processor.service;

import com.becs.processor.config.BecsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Polls the inbox directory for new files and triggers processing.
 * Interval is driven by becs.poll-interval-ms (default 30 s).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InboxPollerService {

    // {bsb_number}.bpy.nnn, e.g. 805050.bpy.001 (nnn is a 3-digit counter, 001-999)
    private static final Pattern INPUT_FILE_PATTERN = Pattern.compile("(?i)\\d+\\.bpy\\.\\d{3}");

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
                    .filter(p -> INPUT_FILE_PATTERN.matcher(p.getFileName().toString()).matches())
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
