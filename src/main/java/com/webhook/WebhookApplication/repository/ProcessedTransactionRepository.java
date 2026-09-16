package com.webhook.WebhookApplication.repository;

import com.webhook.WebhookApplication.entity.ProcessedTransaction;
import org.hibernate.sql.exec.spi.JdbcCallParameterExtractor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProcessedTransactionRepository extends JpaRepository<ProcessedTransaction, Long> {
    Optional<ProcessedTransaction> findByTransactionId(UUID transactionId);
    boolean existsByTransactionId(UUID transactionId);
}
