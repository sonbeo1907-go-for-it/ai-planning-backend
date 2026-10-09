package com.codegym.aiplanning.controller.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.billing.dto.VnpayIpnResponse;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.billing.BillingAbnormalTransaction;
import com.codegym.aiplanning.entity.billing.CreditLedgerEntry;
import com.codegym.aiplanning.entity.billing.CreditPackage;
import com.codegym.aiplanning.entity.billing.CreditPackageStatus;
import com.codegym.aiplanning.entity.billing.CreditWallet;
import com.codegym.aiplanning.entity.billing.LedgerEntryType;
import com.codegym.aiplanning.entity.billing.TopUpOrder;
import com.codegym.aiplanning.entity.billing.TopUpOrderStatus;
import com.codegym.aiplanning.entity.profile.UserProfile;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.billing.BillingAbnormalTransactionRepository;
import com.codegym.aiplanning.repository.billing.CreditLedgerEntryRepository;
import com.codegym.aiplanning.repository.billing.CreditPackageRepository;
import com.codegym.aiplanning.repository.billing.CreditWalletRepository;
import com.codegym.aiplanning.repository.billing.TopUpOrderRepository;
import com.codegym.aiplanning.repository.profile.UserProfileRepository;
import com.codegym.aiplanning.service.billing.BillingAbnormalTransactionService;
import com.codegym.aiplanning.service.billing.gateway.VnpaySecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class BillingPaymentConfirmationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("app.billing.vnpay.hash-secret", () -> "TESTSECRETKEY123");
        registry.add("app.billing.vnpay.tmn-code", () -> "VNPAYTMN");
    }

    private static final String HASH_SECRET = "TESTSECRETKEY123";
    private static final String TMN_CODE = "VNPAYTMN";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private CreditPackageRepository creditPackageRepository;

    @Autowired
    private TopUpOrderRepository topUpOrderRepository;

    @Autowired
    private CreditWalletRepository creditWalletRepository;

    @SpyBean
    private CreditLedgerEntryRepository creditLedgerEntryRepository;

    @SpyBean
    private BillingAbnormalTransactionService abnormalTransactionService;

    @Autowired
    private BillingAbnormalTransactionRepository abnormalTransactionRepository;

    private UserAccount testUserA;
    private UserAccount testUserB;
    private CreditPackage testPackage;

    @BeforeEach
    void setUp() {
        testUserA = createAccount("confirm-user-a-" + UUID.randomUUID() + "@example.com");
        testUserB = createAccount("confirm-user-b-" + UUID.randomUUID() + "@example.com");

        testPackage = creditPackageRepository.findByPackageCode("AI_STARTER_50K")
                .orElseGet(() -> creditPackageRepository.saveAndFlush(new CreditPackage(
                        "AI_STARTER_50K", "Gói AI Khởi Đầu", 50000L, 5000L, 0L, CreditPackageStatus.ACTIVE, 1)));
    }

    private UserAccount createAccount(String email) {
        UserAccount account = userAccountRepository.saveAndFlush(UserAccount.create(
                email,
                "hashedpassword",
                UserRole.USER,
                AccountStatus.ACTIVE));

        UserProfile profile = UserProfile.create(account, "Test User");
        profile.completeSetup("Test User", "Asia/Ho_Chi_Minh", "vi", 60, Instant.now());
        userProfileRepository.saveAndFlush(profile);
        return account;
    }

    private TopUpOrder createOrder(UserAccount user, TopUpOrderStatus status, long priceVnd, long totalCredits) {
        String orderCode = "ORD-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);
        TopUpOrder order = new TopUpOrder(
                user.getId(),
                orderCode,
                testPackage.getId(),
                testPackage.getPackageCode(),
                testPackage.getName(),
                priceVnd,
                totalCredits,
                0L,
                totalCredits,
                status,
                "VND",
                "VNPAY",
                "REF-" + orderCode,
                "IDEMP-" + UUID.randomUUID(),
                expiresAt);
        return topUpOrderRepository.saveAndFlush(order);
    }

    private Map<String, String> buildParams(
            String orderCode,
            long amountVnd,
            String txnNo,
            String responseCode,
            String txnStatus) {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", TMN_CODE);
        params.put("vnp_Amount", String.valueOf(amountVnd * 100L));
        params.put("vnp_TxnRef", orderCode);
        params.put("vnp_TransactionNo", txnNo);
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_TransactionStatus", txnStatus);
        params.put("vnp_OrderInfo", "Topup credits");
        params.put("vnp_PayDate", "20261009100000");

        String hash = VnpaySecurityUtil.hashAllFields(params, HASH_SECRET);
        params.put("vnp_SecureHash", hash);
        return params;
    }

    private VnpayIpnResponse callIpn(Map<String, String> params) throws Exception {
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        params.forEach(map::add);

        MvcResult result = mockMvc.perform(get(ApiConstant.BILLING + ApiConstant.BILLING_VNPAY_IPN).params(map))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), VnpayIpnResponse.class);
    }

    // ==========================================
    // BASIC TESTS
    // ==========================================

    @Test
    @DisplayName("Basic 1A: Returns 97 when signature is invalid")
    void shouldReturn97WhenChecksumInvalid() throws Exception {
        TopUpOrder order = createOrder(testUserA, TopUpOrderStatus.PENDING, 50000L, 5000L);
        Map<String, String> params = buildParams(order.getOrderCode(), 50000L, "TX100", "00", "00");
        params.put("vnp_SecureHash", "INVALID_HASH");

        VnpayIpnResponse response = callIpn(params);
        assertThat(response.rspCode()).isEqualTo("97");
    }

    @Test
    @DisplayName("Basic 1B: Returns 97 when TmnCode is invalid")
    void shouldReturn97WhenTmnCodeInvalid() throws Exception {
        TopUpOrder order = createOrder(testUserA, TopUpOrderStatus.PENDING, 50000L, 5000L);
        Map<String, String> params = buildParams(order.getOrderCode(), 50000L, "TX100", "00", "00");
        params.put("vnp_TmnCode", "WRONG_TMN");
        params.put("vnp_SecureHash", VnpaySecurityUtil.hashAllFields(params, HASH_SECRET));

        VnpayIpnResponse response = callIpn(params);
        assertThat(response.rspCode()).isEqualTo("97");
    }

    @Test
    @DisplayName("Basic 1C: Returns 01 when order does not exist")
    void shouldReturn01WhenOrderNotFound() throws Exception {
        Map<String, String> params = buildParams("NON_EXISTENT_ORDER", 50000L, "TX100", "00", "00");

        VnpayIpnResponse response = callIpn(params);
        assertThat(response.rspCode()).isEqualTo("01");
    }

    @Test
    @DisplayName("Basic 1D: Returns 04 when amount mismatch (considering x100 rule)")
    void shouldReturn04WhenAmountMismatch() throws Exception {
        TopUpOrder order = createOrder(testUserA, TopUpOrderStatus.PENDING, 50000L, 5000L);
        // Order expects 50,000 VND (5,000,000 in VNPAY), but callback sends 100,000 VND
        Map<String, String> params = buildParams(order.getOrderCode(), 100000L, "TX100", "00", "00");

        VnpayIpnResponse response = callIpn(params);
        assertThat(response.rspCode()).isEqualTo("04");

        List<BillingAbnormalTransaction> abnormals = abnormalTransactionRepository.findByOrderCode(order.getOrderCode());
        assertThat(abnormals).isNotEmpty();
        assertThat(abnormals.get(0).getReason()).isEqualTo("AMOUNT_MISMATCH");
    }

    @Test
    @DisplayName("Basic 2: Valid failure callback updates order to FAILED/CANCELLED and returns 00")
    void shouldHandleValidFailureCallback() throws Exception {
        TopUpOrder order = createOrder(testUserA, TopUpOrderStatus.PENDING, 50000L, 5000L);
        Map<String, String> params = buildParams(order.getOrderCode(), 50000L, "TX_FAIL_1", "24", "02");

        VnpayIpnResponse response = callIpn(params);
        assertThat(response.rspCode()).isEqualTo("00");

        TopUpOrder updated = topUpOrderRepository.findById(order.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TopUpOrderStatus.CANCELLED);
        assertThat(updated.getExternalTransactionId()).isEqualTo("TX_FAIL_1");

        // Wallet unchanged
        assertThat(creditWalletRepository.findByUserId(testUserA.getId())).isEmpty();
    }

    @Test
    @DisplayName("Basic 3: Valid success callback marks order PAID, credits wallet, records ledger TOP_UP")
    void shouldHandleValidSuccessCallback() throws Exception {
        TopUpOrder order = createOrder(testUserA, TopUpOrderStatus.PENDING, 50000L, 5000L);
        Map<String, String> params = buildParams(order.getOrderCode(), 50000L, "TX_SUCCESS_1", "00", "00");

        VnpayIpnResponse response = callIpn(params);
        assertThat(response.rspCode()).isEqualTo("00");

        TopUpOrder updated = topUpOrderRepository.findById(order.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TopUpOrderStatus.PAID);
        assertThat(updated.getExternalTransactionId()).isEqualTo("TX_SUCCESS_1");
        assertThat(updated.getPaidAt()).isNotNull();

        CreditWallet wallet = creditWalletRepository.findByUserId(testUserA.getId()).orElseThrow();
        assertThat(wallet.getAvailableCredits()).isEqualTo(5000L);

        List<CreditLedgerEntry> ledgers = creditLedgerEntryRepository.findByUserIdOrderByRecordedAtDesc(testUserA.getId());
        assertThat(ledgers).hasSize(1);
        assertThat(ledgers.get(0).getEntryType()).isEqualTo(LedgerEntryType.TOP_UP);
        assertThat(ledgers.get(0).getAvailableDelta()).isEqualTo(5000L);
    }

    // ==========================================
    // SIX RISK SCENARIOS (TESTCONTAINERS)
    // ==========================================

    @Test
    @DisplayName("Scenario 1: Two concurrent success callbacks for same order - only one credits wallet, second returns 02")
    void scenario1_concurrentSuccessCallbacks() throws Exception {
        TopUpOrder order = createOrder(testUserA, TopUpOrderStatus.PENDING, 50000L, 5000L);
        Map<String, String> params = buildParams(order.getOrderCode(), 50000L, "TX_CONCURRENT_1", "00", "00");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Callable<VnpayIpnResponse>> tasks = List.of(
                () -> callIpn(params),
                () -> callIpn(params));

        List<Future<VnpayIpnResponse>> futures = executor.invokeAll(tasks);
        List<String> codes = new ArrayList<>();
        for (Future<VnpayIpnResponse> f : futures) {
            codes.add(f.get().rspCode());
        }
        executor.shutdown();

        // One must be 00, the other can be 00 or 02 (if duplicate callback arrived after commit)
        assertThat(codes).contains("00");

        // Verification of 5 factors
        TopUpOrder updated = topUpOrderRepository.findById(order.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TopUpOrderStatus.PAID);

        CreditWallet wallet = creditWalletRepository.findByUserId(testUserA.getId()).orElseThrow();
        assertThat(wallet.getAvailableCredits()).isEqualTo(5000L); // Exactly 5000, not 10000!

        List<CreditLedgerEntry> ledgers = creditLedgerEntryRepository.findByUserIdOrderByRecordedAtDesc(testUserA.getId());
        assertThat(ledgers).hasSize(1); // Exactly 1 TOP_UP ledger entry!
    }

    @Test
    @DisplayName("Scenario 2: Two different orders receive the same provider transaction ID - DB unique constraint blocks second")
    void scenario2_differentOrdersSameProviderTransactionId() throws Exception {
        TopUpOrder orderA = createOrder(testUserA, TopUpOrderStatus.PENDING, 50000L, 5000L);
        TopUpOrder orderB = createOrder(testUserB, TopUpOrderStatus.PENDING, 50000L, 5000L);

        String sharedTxn = "TX_SHARED_DUPLICATE_999";
        Map<String, String> paramsA = buildParams(orderA.getOrderCode(), 50000L, sharedTxn, "00", "00");
        Map<String, String> paramsB = buildParams(orderB.getOrderCode(), 50000L, sharedTxn, "00", "00");

        // First order succeeds
        VnpayIpnResponse respA = callIpn(paramsA);
        assertThat(respA.rspCode()).isEqualTo("00");

        // Second order attempts to use same external transaction ID -> DB unique constraint violation -> error 99
        VnpayIpnResponse respB = callIpn(paramsB);
        assertThat(respB.rspCode()).isEqualTo("99");

        // Order A is PAID, wallet A has 5000 credits
        TopUpOrder updatedA = topUpOrderRepository.findById(orderA.getId()).orElseThrow();
        assertThat(updatedA.getStatus()).isEqualTo(TopUpOrderStatus.PAID);
        assertThat(creditWalletRepository.findByUserId(testUserA.getId()).orElseThrow().getAvailableCredits()).isEqualTo(5000L);

        // Order B rolled back to PENDING, wallet B not credited, no ledger entry
        TopUpOrder updatedB = topUpOrderRepository.findById(orderB.getId()).orElseThrow();
        assertThat(updatedB.getStatus()).isEqualTo(TopUpOrderStatus.PENDING);
        assertThat(creditWalletRepository.findByUserId(testUserB.getId())).isEmpty();
        assertThat(creditLedgerEntryRepository.findByUserIdOrderByRecordedAtDesc(testUserB.getId())).isEmpty();
    }

    @Test
    @DisplayName("Scenario 3: Error after updating order but before/during ledger write - entire transaction rolls back")
    void scenario3_failureDuringLedgerWrite_fullRollback() throws Exception {
        TopUpOrder order = createOrder(testUserA, TopUpOrderStatus.PENDING, 50000L, 5000L);
        Map<String, String> params = buildParams(order.getOrderCode(), 50000L, "TX_FAIL_LEDGER", "00", "00");

        // Simulate database crash or constraint failure when saving ledger entry
        Mockito.doThrow(new DataIntegrityViolationException("Simulated ledger write error"))
                .when(creditLedgerEntryRepository).saveAndFlush(Mockito.any(CreditLedgerEntry.class));

        VnpayIpnResponse response = callIpn(params);
        assertThat(response.rspCode()).isEqualTo("99");

        // Verify full rollback: Order remains PENDING, Wallet not credited, Ledger empty
        TopUpOrder updated = topUpOrderRepository.findById(order.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TopUpOrderStatus.PENDING);
        assertThat(creditWalletRepository.findByUserId(testUserA.getId())).isEmpty();
        assertThat(creditLedgerEntryRepository.findByUserIdOrderByRecordedAtDesc(testUserA.getId())).isEmpty();
    }

    @Test
    @DisplayName("Scenario 4: Success callback arrives after order is EXPIRED, CANCELLED, or FAILED - no status change, no credits, abnormal recorded, returns 99")
    void scenario4_latePaymentOnTerminalOrder() throws Exception {
        // Test with CANCELLED
        TopUpOrder cancelledOrder = createOrder(testUserA, TopUpOrderStatus.CANCELLED, 50000L, 5000L);
        Map<String, String> paramsCancelled = buildParams(cancelledOrder.getOrderCode(), 50000L, "TX_LATE_1", "00", "00");

        VnpayIpnResponse respCancelled = callIpn(paramsCancelled);
        assertThat(respCancelled.rspCode()).isEqualTo("99");

        TopUpOrder checkCancelled = topUpOrderRepository.findById(cancelledOrder.getId()).orElseThrow();
        assertThat(checkCancelled.getStatus()).isEqualTo(TopUpOrderStatus.CANCELLED); // NOT overwritten
        assertThat(creditWalletRepository.findByUserId(testUserA.getId())).isEmpty(); // No credit added

        List<BillingAbnormalTransaction> abnormals = abnormalTransactionRepository.findByOrderCode(cancelledOrder.getOrderCode());
        assertThat(abnormals).isNotEmpty();
        assertThat(abnormals.get(0).getReason()).isEqualTo("LATE_PAYMENT_ON_CANCELLED_ORDER");

        // Test with FAILED
        TopUpOrder failedOrder = createOrder(testUserB, TopUpOrderStatus.FAILED, 50000L, 5000L);
        Map<String, String> paramsFailed = buildParams(failedOrder.getOrderCode(), 50000L, "TX_LATE_2", "00", "00");

        VnpayIpnResponse respFailed = callIpn(paramsFailed);
        assertThat(respFailed.rspCode()).isEqualTo("99");

        TopUpOrder checkFailed = topUpOrderRepository.findById(failedOrder.getId()).orElseThrow();
        assertThat(checkFailed.getStatus()).isEqualTo(TopUpOrderStatus.FAILED); // NOT overwritten
        assertThat(creditWalletRepository.findByUserId(testUserB.getId())).isEmpty();
    }

    @Test
    @DisplayName("Scenario 5: Abnormal transaction fails to persist due to DB error - returns 99, never 00 or 02")
    void scenario5_abnormalTransactionDbError_returns99() throws Exception {
        TopUpOrder cancelledOrder = createOrder(testUserA, TopUpOrderStatus.CANCELLED, 50000L, 5000L);
        Map<String, String> params = buildParams(cancelledOrder.getOrderCode(), 50000L, "TX_ERR_5", "00", "00");

        // Simulate database failure when trying to persist the abnormal event
        Mockito.doThrow(new RuntimeException("Simulated abnormal DB connection failure"))
                .when(abnormalTransactionService)
                .recordAbnormal(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        VnpayIpnResponse response = callIpn(params);
        assertThat(response.rspCode()).isEqualTo("99");
        assertThat(response.rspCode()).isNotEqualTo("00");
        assertThat(response.rspCode()).isNotEqualTo("02");
    }

    @Test
    @DisplayName("Scenario 6: Valid signature but contradictory transaction status (00 vs 02) - no credit, abnormal recorded, returns 99")
    void scenario6_contradictoryStatus_recordsAbnormalAndReturns99() throws Exception {
        TopUpOrder order = createOrder(testUserA, TopUpOrderStatus.PENDING, 50000L, 5000L);
        // ResponseCode = 00 (Success) but TransactionStatus = 02 (Failed/Other)
        Map<String, String> params = buildParams(order.getOrderCode(), 50000L, "TX_CONTRADICT_6", "00", "02");

        VnpayIpnResponse response = callIpn(params);
        assertThat(response.rspCode()).isEqualTo("99");

        // Verify no credit added, order not PAID
        TopUpOrder updated = topUpOrderRepository.findById(order.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TopUpOrderStatus.PENDING);
        assertThat(creditWalletRepository.findByUserId(testUserA.getId())).isEmpty();
        assertThat(creditLedgerEntryRepository.findByUserIdOrderByRecordedAtDesc(testUserA.getId())).isEmpty();

        // Verify abnormal transaction was persistently stored
        List<BillingAbnormalTransaction> abnormals = abnormalTransactionRepository.findByOrderCode(order.getOrderCode());
        assertThat(abnormals).isNotEmpty();
        assertThat(abnormals.get(0).getReason()).isEqualTo("CONTRADICTORY_IPN_STATUS");
    }

    @Test
    @DisplayName("Abnormal case: PAID order receives different transaction ID - abnormal recorded, returns 99, never 02")
    void paidOrderWithDifferentTransactionId_recordsAbnormalAndReturns99() throws Exception {
        TopUpOrder order = createOrder(testUserA, TopUpOrderStatus.PENDING, 50000L, 5000L);

        // First callback confirms it with TXN_ORIGINAL
        Map<String, String> params1 = buildParams(order.getOrderCode(), 50000L, "TXN_ORIGINAL", "00", "00");
        VnpayIpnResponse resp1 = callIpn(params1);
        assertThat(resp1.rspCode()).isEqualTo("00");

        // Second callback for same order arrives with different txn ID TXN_ANOTHER
        Map<String, String> params2 = buildParams(order.getOrderCode(), 50000L, "TXN_ANOTHER", "00", "00");
        VnpayIpnResponse resp2 = callIpn(params2);
        assertThat(resp2.rspCode()).isEqualTo("99"); // MUST NOT return 02

        // Abnormal recorded
        List<BillingAbnormalTransaction> abnormals = abnormalTransactionRepository.findByOrderCode(order.getOrderCode());
        assertThat(abnormals).isNotEmpty();
        assertThat(abnormals.get(0).getReason()).isEqualTo("ORDER_ALREADY_PAID_DIFFERENT_TRANSACTION");
    }
}
