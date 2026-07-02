package com.becs.processor.repository;

import com.becs.processor.model.BsbSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BsbSequenceRepository extends JpaRepository<BsbSequence, String> {
}
