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
 * 001) used for a given BSB number, so it can be looked up instead of
 * scanning the output directory on every file.
 */
@Entity
@Table(name = "becs_bsb_sequence")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BsbSequence {

    @Id
    @Column(name = "bsb_number", length = 32)
    private String bsbNumber;

    @Column(name = "last_sequence", nullable = false)
    private Integer lastSequence;
}
