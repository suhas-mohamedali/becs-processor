package com.becs.processor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

@Data
@Configuration
@ConfigurationProperties(prefix = "becs")
public class BecsProperties {

    private String inboxDir  = "/data/becs/inbox";
    private String archiveDir = "/data/becs/archive";
    private String outputDir  = "/data/becs/output";
    private String errorDir   = "/data/becs/error";
    private long   pollIntervalMs = 30_000L;
    private int    chunkSize      = 100;

    public Path inbox()   { return Paths.get(inboxDir); }
    public Path archive() { return Paths.get(archiveDir); }
    public Path output()  { return Paths.get(outputDir); }
    public Path error()   { return Paths.get(errorDir); }
}
