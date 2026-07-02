package com.becs.processor.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data @Builder
public class ParsedBpyFile {
    private ParsedHeader        header;
    private List<ParsedPayment> payments;
    private ParsedTrailer       trailer;
}
