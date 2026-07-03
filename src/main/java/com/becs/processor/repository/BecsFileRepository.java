package com.becs.processor.repository;

import com.becs.processor.model.BecsFile;
import com.becs.processor.model.BecsFileStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BecsFileRepository extends JpaRepository<BecsFile, Long> {

    List<BecsFile> findByStatusOrderByReceivedAtAsc(BecsFileStatus status);

    Optional<BecsFile> findByFileName(String fileName);

    boolean existsByFileNameAndStatusNot(String fileName, BecsFileStatus status);
}
