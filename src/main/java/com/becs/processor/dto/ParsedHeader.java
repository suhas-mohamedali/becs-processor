package com.becs.processor.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data @Builder
public class ParsedHeader {
    private String    reelSequenceNumber;
    private String    financialInstitution;
    private String    userPreferredSpec;
    private String    userId;
    private String    description;
    private LocalDate processingDate;
    private int       lineNumber;
}
