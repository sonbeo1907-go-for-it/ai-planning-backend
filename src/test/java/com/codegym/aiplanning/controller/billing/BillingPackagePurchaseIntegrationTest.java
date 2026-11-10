package com.codegym.aiplanning.controller.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.controller.billing.dto.CreateTopUpOrderRequest;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.billing.CreditPackage;
import com.codegym.aiplanning.entity.billing.CreditPackageStatus;
import com.codegym.aiplanning.entity.billing.TopUpOrder;
import com.codegym.aiplanning.entity.billing.TopUpOrderStatus;
import com.codegym.aiplanning.entity.profile.UserProfile;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.billing.CreditPackageRepository;
import com.codegym.aiplanning.repository.billing.TopUpOrderRepository;
import com.codegym.aiplanning.repository.profile.UserProfileRepository;
import com.codegym.aiplanning.service.billing.gateway.PaymentGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class BillingPackagePurchaseIntegrationTest {

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
    private PasswordEncoder passwordEncoder;

    @SpyBean
    private PaymentGateway paymentGateway;

    private UserAccount testUserA;
    private UserAccount testUserB;
    private String tokenA;
    private String tokenB;
    private CreditPackage activePackageStarter;
    private CreditPackage activePackagePro;
    private CreditPackage archivedPackage;

    @BeforeEach
    void setUp() throws Exception {
        testUserA = createAccount("user-a-" + UUID.randomUUID() + "@example.com");
        testUserB = createAccount("user-b-" + UUID.randomUUID() + "@example.com");
        tokenA = login(testUserA);
        tokenB = login(testUserB);

        activePackageStarter = creditPackageRepository.findByPackageCode("AI_STARTER_50K")
                .orElseGet(() -> creditPackageRepository.saveAndFlush(new CreditPackage(
                        "AI_STARTER_50K", "Gói AI Khởi Đầu", 50000L, 5000L, 0L, CreditPackageStatus.ACTIVE, 1)));

        activePackagePro = creditPackageRepository.findByPackageCode("AI_PRO_100K")
                .orElseGet(() -> creditPackageRepository.saveAndFlush(new CreditPackage(
                        "AI_PRO_100K", "Gói AI Chuyên Nghiệp", 100000L, 10000L, 1000L, CreditPackageStatus.ACTIVE, 2)));

        archivedPackage = creditPackageRepository.findByPackageCode("AI_LEGACY_ARCHIVED")
                .orElseGet(() -> creditPackageRepository.saveAndFlush(new CreditPackage(
                        "AI_LEGACY_ARCHIVED", "Gói Cũ Hết Hạn", 30000L, 3000L, 0L, CreditPackageStatus.ARCHIVED, 99)));
    }

    @Test
    @DisplayName("GET /api/v1/billing/packages returns only ACTIVE packages, ordered by sortOrder")
    void getPackages_returnsOnlyActivePackages() throws Exception {
        MvcResult result = mockMvc.perform(get(ApiConstant.BILLING_PACKAGES)
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        JsonNode data = data(result);
        assertThat(data.size()).isGreaterThanOrEqualTo(2);

        for (JsonNode item : data) {
            assertThat(item.path("status").asText()).isEqualTo("ACTIVE");
            long base = item.path("baseCredits").asLong();
            long bonus = item.path("bonusCredits").asLong();
            long total = item.path("totalCredits").asLong();
            assertThat(total).isEqualTo(base + bonus);
            assertThat(item.path("priceVnd").asLong()).isGreaterThan(0);
        }

        // ARCHIVED package should not be present in response
        boolean hasArchived = false;
        for (JsonNode item : data) {
            if ("AI_LEGACY_ARCHIVED".equals(item.path("packageCode").asText())) {
                hasArchived = true;
            }
        }
        assertThat(hasArchived).isFalse();
    }

    @Test
    @DisplayName("POST /api/v1/billing/top-up-orders successfully creates PENDING order with snapshot and checkoutUrl")
    void createOrder_success() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        CreateTopUpOrderRequest request = new CreateTopUpOrderRequest(activePackagePro.getId());

        MvcResult result = mockMvc.perform(post(ApiConstant.BILLING_TOP_UP_ORDERS)
                        .header("Authorization", bearer(tokenA))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.currency").value("VND"))
                .andExpect(jsonPath("$.data.packageCode").value("AI_PRO_100K"))
                .andExpect(jsonPath("$.data.packageName").value(activePackagePro.getName()))
                .andExpect(jsonPath("$.data.priceVnd").value(100000L))
                .andExpect(jsonPath("$.data.baseCredits").value(10000L))
                .andExpect(jsonPath("$.data.bonusCredits").value(1000L))
                .andExpect(jsonPath("$.data.totalCredits").value(11000L))
                .andExpect(jsonPath("$.data.checkoutUrl").isString())
                .andReturn();

        JsonNode data = data(result);
        UUID orderId = UUID.fromString(data.path("id").asText());

        TopUpOrder saved = topUpOrderRepository.findById(orderId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(TopUpOrderStatus.PENDING);
        assertThat(saved.getUserId()).isEqualTo(testUserA.getId());
        assertThat(saved.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());
        assertThat(saved.getTotalCreditsSnapshot()).isEqualTo(11000L);
    }

    @Test
    @DisplayName("POST /api/v1/billing/top-up-orders rejects non-existent package with 404 CREDIT_PACKAGE_NOT_FOUND")
    void createOrder_nonExistentPackage_returns404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        CreateTopUpOrderRequest request = new CreateTopUpOrderRequest(unknownId);

        mockMvc.perform(post(ApiConstant.BILLING_TOP_UP_ORDERS)
                        .header("Authorization", bearer(tokenA))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREDIT_PACKAGE_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/v1/billing/top-up-orders rejects ARCHIVED package with 400 CREDIT_PACKAGE_NOT_ACTIVE")
    void createOrder_archivedPackage_returns400() throws Exception {
        CreateTopUpOrderRequest request = new CreateTopUpOrderRequest(archivedPackage.getId());

        mockMvc.perform(post(ApiConstant.BILLING_TOP_UP_ORDERS)
                        .header("Authorization", bearer(tokenA))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CREDIT_PACKAGE_NOT_ACTIVE"));
    }

    @Test
    @DisplayName("POST /api/v1/billing/top-up-orders rejects packages outside configured min/max limits")
    void createOrder_minMaxOutOfBounds_returns400() throws Exception {
        CreditPackage cheapPkg = creditPackageRepository.saveAndFlush(new CreditPackage(
                "AI_CHEAP_" + UUID.randomUUID().toString().substring(0, 8),
                "Quá Rẻ",
                10000L, // < 20,000 VND
                1000L,
                0L,
                CreditPackageStatus.ACTIVE,
                10));

        CreditPackage expensivePkg = creditPackageRepository.saveAndFlush(new CreditPackage(
                "AI_EXPENSIVE_" + UUID.randomUUID().toString().substring(0, 8),
                "Quá Đắt",
                5000000L, // > 2,000,000 VND
                500000L,
                0L,
                CreditPackageStatus.ACTIVE,
                11));

        // Test below min
        mockMvc.perform(post(ApiConstant.BILLING_TOP_UP_ORDERS)
                        .header("Authorization", bearer(tokenA))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTopUpOrderRequest(cheapPkg.getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOP_UP_AMOUNT_OUT_OF_BOUNDS"));

        // Test above max
        mockMvc.perform(post(ApiConstant.BILLING_TOP_UP_ORDERS)
                        .header("Authorization", bearer(tokenA))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTopUpOrderRequest(expensivePkg.getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOP_UP_AMOUNT_OUT_OF_BOUNDS"));
    }

    @Test
    @DisplayName("Snapshot Immutability: package modification after order creation does not alter historical order")
    void snapshotImmutability_preservesHistoricalOrderData() throws Exception {
        CreditPackage mutablePkg = creditPackageRepository.saveAndFlush(new CreditPackage(
                "AI_MUTABLE_" + UUID.randomUUID().toString().substring(0, 8),
                "Gói Thay Đổi",
                50000L,
                5000L,
                0L,
                CreditPackageStatus.ACTIVE,
                50));

        String idempotencyKey = UUID.randomUUID().toString();
        CreateTopUpOrderRequest request = new CreateTopUpOrderRequest(mutablePkg.getId());

        MvcResult result = mockMvc.perform(post(ApiConstant.BILLING_TOP_UP_ORDERS)
                        .header("Authorization", bearer(tokenA))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        UUID orderId = UUID.fromString(data(result).path("id").asText());

        // Now Admin modifies the original package
        mutablePkg.setPriceVnd(150000L);
        mutablePkg.setBaseCredits(15000L);
        mutablePkg.setStatus(CreditPackageStatus.ARCHIVED);
        creditPackageRepository.saveAndFlush(mutablePkg);

        // Fetch order detail from DB and API
        TopUpOrder order = topUpOrderRepository.findById(orderId).orElseThrow();
        assertThat(order.getPriceVndSnapshot()).isEqualTo(50000L); // original price preserved!
        assertThat(order.getBaseCreditsSnapshot()).isEqualTo(5000L); // original credits preserved!
        assertThat(order.getTotalCreditsSnapshot()).isEqualTo(5000L);

        // API GET order also returns the original snapshot
        mockMvc.perform(get(ApiConstant.BILLING_TOP_UP_ORDERS + "/" + orderId)
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priceVnd").value(50000L))
                .andExpect(jsonPath("$.data.baseCredits").value(5000L));
    }

    @Test
    @DisplayName("Idempotency: repeating same request with same key returns identical order without duplicate insert")
    void idempotency_sameKeySamePackage_returnsSameOrder() throws Exception {
        String idempotencyKey = "TEST-KEY-" + UUID.randomUUID();
        CreateTopUpOrderRequest request = new CreateTopUpOrderRequest(activePackageStarter.getId());

        // First attempt
        MvcResult res1 = mockMvc.perform(post(ApiConstant.BILLING_TOP_UP_ORDERS)
                        .header("Authorization", bearer(tokenA))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        String orderId1 = data(res1).path("id").asText();

        // Second attempt with exact same key
        MvcResult res2 = mockMvc.perform(post(ApiConstant.BILLING_TOP_UP_ORDERS)
                        .header("Authorization", bearer(tokenA))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        String orderId2 = data(res2).path("id").asText();

        assertThat(orderId1).isEqualTo(orderId2);

        // Verify only 1 order exists in database for this key
        long count = topUpOrderRepository.findAll().stream()
                .filter(o -> o.getUserId().equals(testUserA.getId()) && idempotencyKey.equals(o.getIdempotencyKey()))
                .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("Idempotency Conflict: same key with different package throws 409 TOP_UP_IDEMPOTENCY_CONFLICT")
    void idempotencyConflict_sameKeyDifferentPackage_returns409() throws Exception {
        String idempotencyKey = "TEST-CONFLICT-" + UUID.randomUUID();

        // First attempt with Starter package
        mockMvc.perform(post(ApiConstant.BILLING_TOP_UP_ORDERS)
                        .header("Authorization", bearer(tokenA))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTopUpOrderRequest(activePackageStarter.getId()))))
                .andExpect(status().isOk());

        // Second attempt with Pro package and same key -> 409 Conflict
        mockMvc.perform(post(ApiConstant.BILLING_TOP_UP_ORDERS)
                        .header("Authorization", bearer(tokenA))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTopUpOrderRequest(activePackagePro.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOP_UP_IDEMPOTENCY_CONFLICT"));
    }

    @Test
    @DisplayName("Idempotency Isolation: User A and User B can use same key independently")
    void idempotency_differentUsersCanUseSameKey() throws Exception {
        String sharedKey = "SHARED-KEY-" + UUID.randomUUID();

        MvcResult resA = mockMvc.perform(post(ApiConstant.BILLING_TOP_UP_ORDERS)
                        .header("Authorization", bearer(tokenA))
                        .header("Idempotency-Key", sharedKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTopUpOrderRequest(activePackageStarter.getId()))))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult resB = mockMvc.perform(post(ApiConstant.BILLING_TOP_UP_ORDERS)
                        .header("Authorization", bearer(tokenB))
                        .header("Idempotency-Key", sharedKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTopUpOrderRequest(activePackageStarter.getId()))))
                .andExpect(status().isOk())
                .andReturn();

        UUID idA = UUID.fromString(data(resA).path("id").asText());
        UUID idB = UUID.fromString(data(resB).path("id").asText());
        assertThat(idA).isNotEqualTo(idB);
    }

    @Test
    @DisplayName("Concurrency Protection: Multiple simultaneous requests with same key produce exactly one order")
    void concurrencyProtection_producesOnlyOneOrder() throws Exception {
        String idempotencyKey = "CONCURRENT-" + UUID.randomUUID();
        CreateTopUpOrderRequest request = new CreateTopUpOrderRequest(activePackageStarter.getId());

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Callable<Integer>> tasks = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                MvcResult result = mockMvc.perform(post(ApiConstant.BILLING_TOP_UP_ORDERS)
                                .header("Authorization", bearer(tokenA))
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andReturn();
                return result.getResponse().getStatus();
            });
        }

        List<Future<Integer>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        for (Future<Integer> f : futures) {
            assertThat(f.get()).isEqualTo(200);
        }

        // Verify only 1 order created in DB
        long count = topUpOrderRepository.findAll().stream()
                .filter(o -> o.getUserId().equals(testUserA.getId()) && idempotencyKey.equals(o.getIdempotencyKey()))
                .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("Phase 2 Failure Semantics: Gateway exception leaves order in PENDING status and returns 503")
    void phase2Failure_leavesOrderInPendingStatus_returns503() throws Exception {
        String idempotencyKey = "GATEWAY-FAIL-" + UUID.randomUUID();
        CreateTopUpOrderRequest request = new CreateTopUpOrderRequest(activePackageStarter.getId());

        // Simulate Gateway network outage
        Mockito.doThrow(new RuntimeException("Connection timed out to gateway"))
                .when(paymentGateway).createCheckout(Mockito.any());

        mockMvc.perform(post(ApiConstant.BILLING_TOP_UP_ORDERS)
                        .header("Authorization", bearer(tokenA))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_PROVIDER_UNAVAILABLE"));

        // Reset mock
        Mockito.reset(paymentGateway);

        // Verify in DB: Order was committed before gateway call and remains in PENDING status
        TopUpOrder order = topUpOrderRepository.findByUserIdAndIdempotencyKey(testUserA.getId(), idempotencyKey)
                .orElseThrow();
        assertThat(order.getStatus()).isEqualTo(TopUpOrderStatus.PENDING);
    }

    @Test
    @DisplayName("Security Isolation: User A cannot read User B's order")
    void securityIsolation_userCannotReadOtherUsersOrder() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        CreateTopUpOrderRequest request = new CreateTopUpOrderRequest(activePackageStarter.getId());

        MvcResult result = mockMvc.perform(post(ApiConstant.BILLING_TOP_UP_ORDERS)
                        .header("Authorization", bearer(tokenB))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        UUID orderIdB = UUID.fromString(data(result).path("id").asText());

        // User A attempts to view User B's order
        mockMvc.perform(get(ApiConstant.BILLING_TOP_UP_ORDERS + "/" + orderIdB)
                        .header("Authorization", bearer(tokenA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOP_UP_ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/v1/billing/top-up-orders rejects missing or blank Idempotency-Key with 400")
    void createOrder_missingOrBlankIdempotencyKey_returns400() throws Exception {
        CreateTopUpOrderRequest request = new CreateTopUpOrderRequest(activePackageStarter.getId());

        // Missing header
        mockMvc.perform(post(ApiConstant.BILLING_TOP_UP_ORDERS)
                        .header("Authorization", bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // Blank header
        mockMvc.perform(post(ApiConstant.BILLING_TOP_UP_ORDERS)
                        .header("Authorization", bearer(tokenA))
                        .header("Idempotency-Key", "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /api/v1/billing/top-up-orders rejects Idempotency-Key exceeding 128 characters with 400")
    void createOrder_idempotencyKeyTooLong_returns400() throws Exception {
        String longKey = "A".repeat(129);
        CreateTopUpOrderRequest request = new CreateTopUpOrderRequest(activePackageStarter.getId());

        mockMvc.perform(post(ApiConstant.BILLING_TOP_UP_ORDERS)
                        .header("Authorization", bearer(tokenA))
                        .header("Idempotency-Key", longKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private UserAccount createAccount(String email) {
        UserAccount account = userAccountRepository.saveAndFlush(UserAccount.create(
                email,
                passwordEncoder.encode("Password@123"),
                UserRole.USER,
                AccountStatus.ACTIVE));
        UserProfile profile = UserProfile.create(account, "Billing User");
        profile.completeSetup(
                "Billing User", "Asia/Ho_Chi_Minh", "vi", 60, Instant.now());
        userProfileRepository.saveAndFlush(profile);
        return account;
    }

    private String login(UserAccount account) throws Exception {
        MvcResult result = mockMvc.perform(post(ApiConstant.AUTH_LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", account.getEmail(),
                                "password", "Password@123"))))
                .andExpect(status().isOk())
                .andReturn();
        return data(result).path("accessToken").asText();
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .path("data");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
