package com.webhook.WebhookApplication.exception;

import java.util.UUID;

public class DuplicateTransactionException extends RuntimeException {

    private final UUID transactionId;
    public DuplicateTransactionException(UUID transactionId) {
        super("Duplicate transaction: " + transactionId);
        this.transactionId = transactionId;
    }
    public UUID getTransactionId() {
        return transactionId;
    }
}
