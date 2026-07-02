package com.becs.processor.repository;

import com.becs.processor.model.BpyFile;
import com.becs.processor.model.BpyFileStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BpyFileRepository extends JpaRepository<BpyFile, Long> {

    List<BpyFile> findByStatusOrderByReceivedAtAsc(BpyFileStatus status);

    Optional<BpyFile> findByFileName(String fileName);

    boolean existsByFileNameAndStatusNot(String fileName, BpyFileStatus status);
}
