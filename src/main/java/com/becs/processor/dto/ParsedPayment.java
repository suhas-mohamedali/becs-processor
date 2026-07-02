package com.becs.processor.dto;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class ParsedPayment {
    private String  bsbNumber;
    private String  accountNumber;
    private String  indicator;
    private String  transactionCode;
    private Long    amountCents;
    private String  accountName;
    private String  lodgementReference;
    private String  traceBsb;
    private String  traceAccount;
    private String  remitterName;
    private Long    withholdingTax;
    private int     lineNumber;
    private String  recordType;
}
