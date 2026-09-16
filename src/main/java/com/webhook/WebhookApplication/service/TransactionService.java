package com.webhook.WebhookApplication.service;


import com.webhook.WebhookApplication.dto.TransactionRequest;
import com.webhook.WebhookApplication.dto.TransactionResponse;
import com.webhook.WebhookApplication.entity.ProcessedTransaction;
import com.webhook.WebhookApplication.entity.Wallet;
import com.webhook.WebhookApplication.exception.DuplicateTransactionException;
import com.webhook.WebhookApplication.exception.InsufficientFundsException;
import com.webhook.WebhookApplication.repository.ProcessedTransactionRepository;
import com.webhook.WebhookApplication.repository.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.webhook.WebhookApplication.enums.TransactionType;

import java.math.BigDecimal;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    @Autowired
    private final WalletRepository  walletRepository;

    @Autowired
    private final ProcessedTransactionRepository  processedTransactionRepository;

    public TransactionService(WalletRepository walletRepository,
                              ProcessedTransactionRepository processedTransactionRepository) {
        this.walletRepository = walletRepository;
        this.processedTransactionRepository = processedTransactionRepository;
    }

    @Transactional
    public TransactionResponse processTransaction(TransactionRequest request) {

        if (processedTransactionRepository.existsByTransactionId(request.getTransactionId())) {
            log.info("DUPLICATE detected (fast path): {}", request.getTransactionId());
            throw new DuplicateTransactionException(request.getTransactionId());
        }

        Wallet wallet = walletRepository.findByUserIdWithLock(request.getUserId())
                .orElseThrow(() -> new RuntimeException("Wallet not found for userId: " + request.getUserId()));

        ProcessedTransaction txn = new ProcessedTransaction(
                request.getTransactionId(),
                request.getUserId(),
                request.getAmount(),
                request.getType().name(),
                "PENDING"
        );
        try {
            processedTransactionRepository.saveAndFlush(txn);
        } catch (DataIntegrityViolationException e) {
            log.info("DUPLICATE detected (DB constraint): {}", request.getTransactionId());
            throw new DuplicateTransactionException(request.getTransactionId());
        }

        BigDecimal currentBalance = wallet.getBalance();
        if (request.getType() == TransactionType.DEBIT) {
            if (currentBalance.compareTo(request.getAmount()) < 0) {
                txn.setStatus("FAILED");
                processedTransactionRepository.save(txn);
                log.info("INSUFFICIENT FUNDS: txnId={}, balance={}, amount={}",
                        request.getTransactionId(), currentBalance, request.getAmount());
                throw new InsufficientFundsException(currentBalance, request.getAmount());
            }
            wallet.setBalance(currentBalance.subtract(request.getAmount()));
        } else {

            wallet.setBalance(currentBalance.add(request.getAmount()));
        }
        walletRepository.save(wallet);
        txn.setStatus("SUCCESS");
        processedTransactionRepository.save(txn);
        log.info("SUCCESS: txnId={}, newBalance={}", request.getTransactionId(), wallet.getBalance());
        return new TransactionResponse(
                request.getTransactionId(),
                "SUCCESS",
                wallet.getBalance(),
                "Transaction processed successfully"
        );
    }
}
