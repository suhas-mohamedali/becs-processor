package com.becs.processor.service;

import com.becs.processor.config.BecsProperties;
import com.becs.processor.model.FileType;
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
 * Polls inbox/bpy and inbox/ret for new files and triggers processing.
 * Interval is driven by becs.poll-interval-ms (default 30 s).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InboxPollerService {

    // {bsb_number}.bpy.nnn, e.g. 805050.bpy.001 (nnn is a 3-digit counter, 001-999)
    private static final Pattern BPY_FILE_PATTERN = Pattern.compile("(?i)\\d+\\.bpy\\.\\d{3}");

    // {bsb_number}.ret.nnn, e.g. 805050.ret.511 (nnn is a 3-digit counter, 001-999)
    private static final Pattern RET_FILE_PATTERN = Pattern.compile("(?i)\\d+\\.ret\\.\\d{3}");

    // {bsb_number}.nde.nnn, e.g. 805050.nde.376 (nnn is a 3-digit counter, 001-999)
    private static final Pattern NDE_FILE_PATTERN = Pattern.compile("(?i)\\d+\\.nde\\.\\d{3}");

    private final BecsProperties      props;
    private final BecsProcessingService processingService;

    @Scheduled(fixedDelayString = "${becs.poll-interval-ms:30000}",
               initialDelayString = "${becs.poll-interval-ms:30000}")
    public void poll() {
        pollDirectory(props.inboxBpy(), BPY_FILE_PATTERN, FileType.BPY);
        pollDirectory(props.inboxRet(), RET_FILE_PATTERN, FileType.RET);
        pollDirectory(props.inboxNde(), NDE_FILE_PATTERN, FileType.NDE);
    }

    private void pollDirectory(Path dir, Pattern pattern, FileType fileType) {
        log.debug("Polling {} inbox: {}", fileType, dir);

        List<Path> files;
        try (Stream<Path> stream = Files.list(dir)) {
            files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> pattern.matcher(p.getFileName().toString()).matches())
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Cannot read {} inbox directory {}: {}", fileType, dir, e.getMessage());
            return;
        }

        if (files.isEmpty()) {
            log.debug("No {} files found in inbox", fileType);
            return;
        }

        log.info("Found {} {} file(s) in inbox", files.size(), fileType);
        for (Path file : files) {
            try {
                processingService.process(file, fileType);
            } catch (Exception e) {
                log.error("Unhandled error processing {}: {}", file.getFileName(), e.getMessage(), e);
            }
        }
    }
}
