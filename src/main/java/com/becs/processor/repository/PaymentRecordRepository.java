package com.becs.processor.repository;

import com.becs.processor.model.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {

    List<PaymentRecord> findByBpyFileId(Long bpyFileId);

    @Query("SELECT COUNT(p) FROM PaymentRecord p WHERE p.bpyFile.id = :fileId")
    long countByBpyFileId(Long fileId);

    @Query("SELECT SUM(p.amountCents) FROM PaymentRecord p WHERE p.bpyFile.id = :fileId AND p.transactionCode IN ('50','51','52','53','54','55')")
    Long sumCreditsByFileId(Long fileId);

    @Query("SELECT SUM(p.amountCents) FROM PaymentRecord p WHERE p.bpyFile.id = :fileId AND p.transactionCode = '13'")
    Long sumDebitsByFileId(Long fileId);
}
