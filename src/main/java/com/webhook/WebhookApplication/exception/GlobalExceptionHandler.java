package com.webhook.WebhookApplication.exception;


import com.webhook.WebhookApplication.dto.TransactionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateTransactionException.class)
    public ResponseEntity<TransactionResponse> handleDuplicate(DuplicateTransactionException ex) {
        TransactionResponse response = new TransactionResponse(
                ex.getTransactionId(),
                "DUPLICATE",
                null,
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response); // 409
    }
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<TransactionResponse> handleInsufficientFunds(InsufficientFundsException ex) {
        TransactionResponse response = new TransactionResponse(
                null,
                "FAILED",
                null,
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response); // 400
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(errors);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneral(Exception ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

}
