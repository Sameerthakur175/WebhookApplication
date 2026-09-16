package com.webhook.WebhookApplication;


import com.webhook.WebhookApplication.dto.TransactionRequest;
import com.webhook.WebhookApplication.entity.Wallet;
import com.webhook.WebhookApplication.repository.ProcessedTransactionRepository;
import com.webhook.WebhookApplication.repository.WalletRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import com.webhook.WebhookApplication.enums.TransactionType;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TransactionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private ProcessedTransactionRepository processedTransactionRepository;

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @BeforeEach
    void setUp() {

        processedTransactionRepository.deleteAll();
        walletRepository.deleteAll();

        Wallet wallet = new Wallet(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                USER_ID,
                new BigDecimal("500.00")
        );
        walletRepository.saveAndFlush(wallet);
    }

    @Test
    @Order(1)
    @DisplayName("Processes a single valid debit transaction successfully")
    void happyPath_singleDebit() throws Exception {
        UUID txnId = UUID.randomUUID();
        TransactionRequest request = new TransactionRequest(
                txnId, USER_ID, new BigDecimal("250.00"),
                TransactionType.DEBIT
        );
        MvcResult result = mockMvc.perform(post("/api/v1/transactions/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
        int status = result.getResponse().getStatus();
        String body = result.getResponse().getContentAsString();
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("TEST 1: Happy Path — Single Debit");
        System.out.println("HTTP Status: " + status);
        System.out.println("Response: " + body);
        System.out.println("═══════════════════════════════════════════════════");
        assertThat(status).isEqualTo(200);
        assertThat(body).contains("SUCCESS");
        // Verify balance is ₹250
        Wallet wallet = walletRepository.findAll().get(0);
        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("250.00"));
        System.out.println("✅ Balance after debit: ₹" + wallet.getBalance());
    }


    @Test
    @Order(2)
    @DisplayName("Sends 3 identical transactionIDs simultaneously — balance deducted only once")
    void idempotency_triplicateRequests() throws Exception {
        UUID txnId = UUID.randomUUID();
        TransactionRequest request = new TransactionRequest(
                txnId, USER_ID, new BigDecimal("100.00"),
                TransactionType.DEBIT
        );
        String jsonPayload = objectMapper.writeValueAsString(request);

        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            futures.add(executor.submit(() -> {
                latch.await();
                return mockMvc.perform(post("/api/v1/transactions/process")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonPayload))
                        .andReturn();
            }));
        }
        latch.countDown();

        int successCount = 0;
        int conflictCount = 0;
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("TEST 2: Idempotency — 3 Identical TransactionIDs");
        for (int i = 0; i < futures.size(); i++) {
            MvcResult result = futures.get(i).get(5, TimeUnit.SECONDS);
            int status = result.getResponse().getStatus();
            System.out.println("  Request " + (i + 1) + " → HTTP " + status);
            if (status == 200) successCount++;
            else if (status == 409) conflictCount++;
        }
        executor.shutdown();
        System.out.println("  Success: " + successCount + " | Conflict: " + conflictCount);
        System.out.println("═══════════════════════════════════════════════════");

        assertThat(successCount).isEqualTo(1);
        assertThat(conflictCount).isEqualTo(2);

        Wallet wallet = walletRepository.findAll().get(0);
        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("400.00"));
        System.out.println("✅ Balance deducted exactly once: ₹" + wallet.getBalance());
    }


    @Test
    @Order(3)
    @DisplayName("Sends 10 concurrent ₹100 debits for ₹500 wallet — final balance is ₹0, 5 fail with insufficient funds")
    void raceCondition_concurrentDebits() throws Exception {

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            UUID txnId = UUID.randomUUID();
            TransactionRequest request = new TransactionRequest(
                    txnId, USER_ID, new BigDecimal("100.00"),
                    TransactionType.DEBIT
            );
            String json = objectMapper.writeValueAsString(request);
            futures.add(executor.submit(() -> {
                latch.await();
                return mockMvc.perform(post("/api/v1/transactions/process")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                        .andReturn();
            }));
        }
        latch.countDown();

        int successCount = 0;
        int failedCount = 0;
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("TEST 3: Race Condition — 10 Concurrent ₹100 Debits");
        for (int i = 0; i < futures.size(); i++) {
            MvcResult result = futures.get(i).get(10, TimeUnit.SECONDS);
            int status = result.getResponse().getStatus();
            System.out.println("  Request " + (i + 1) + " → HTTP " + status);
            if (status == 200) successCount++;
            else if (status == 400) failedCount++;
        }
        executor.shutdown();
        System.out.println("  Succeeded: " + successCount + " | Failed (insufficient funds): " + failedCount);
        System.out.println("═══════════════════════════════════════════════════");

        assertThat(successCount).isEqualTo(5);
        assertThat(failedCount).isEqualTo(5);

        Wallet wallet = walletRepository.findAll().get(0);
        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("0.00"));
        System.out.println("✅ Final balance: ₹" + wallet.getBalance());
        System.out.println("✅ Exactly 5 succeeded, 5 rejected — no negative balance!");
    }
}
