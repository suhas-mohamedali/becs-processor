package com.becs.processor.dto;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class ParsedTrailer {
    private String  bsbFiller;
    private Long    netTotalAmount;
    private Long    creditTotalAmount;
    private Long    debitTotalAmount;
    private Integer recordCount;
    private int     lineNumber;
}
