package com.becs.processor.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data @Builder
public class ParsedBecsFile {
    private List<ParsedHeader>  headers;
    private List<ParsedPayment> payments;
    private List<ParsedTrailer> trailers;
}
