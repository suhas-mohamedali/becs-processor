package com.becs.processor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tracks the last debulked-output sequence number (001-999, wraps back to
 * 001) used for a given BSB number + output file type (e.g. "12345:BPY",
 * "12345:RET"), so it can be looked up instead of scanning the output
 * directory on every file. BPY and RET outputs for the same BSB number
 * count independently.
 */
@Entity
@Table(name = "becs_bsb_sequence")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BsbSequence {

    @Id
    @Column(name = "sequence_key", length = 40)
    private String sequenceKey;

    @Column(name = "last_sequence", nullable = false)
    private Integer lastSequence;
}
