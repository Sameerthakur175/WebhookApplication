package com.webhook.WebhookApplication.dto;


import lombok.Getter;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Service
public class TransactionResponse {

    private UUID transactionId;
    private String status;
    private BigDecimal newBalance;
    private String message;


    public TransactionResponse() {}


    public TransactionResponse(UUID transactionId, String status, BigDecimal newBalance, String message) {
        this.transactionId = transactionId;
        this.status = status;
        this.newBalance = newBalance;
        this.message = message;
    }
}
