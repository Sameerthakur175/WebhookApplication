package com.webhook.WebhookApplication.dto;

import com.webhook.WebhookApplication.enums.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;


@Getter
@Setter
public class TransactionRequest {

    @NotNull(message = "transactionId is Required")
    private UUID transactionId;

    @NotNull(message = "userId is Required")
    private UUID userId;

    @NotNull(message = "amount is Required")
    @DecimalMin(value = "0.01", message = "amount must be greater than 1")
    private BigDecimal amount;

    @NotNull(message = "type is Required")
    private TransactionType type;


    public TransactionRequest() {}

    public TransactionRequest(UUID transactionId, UUID userId, BigDecimal amount, TransactionType type) {
        this.transactionId = transactionId;
        this.userId = userId;
        this.amount = amount;
        this.type = type;
    }


}
