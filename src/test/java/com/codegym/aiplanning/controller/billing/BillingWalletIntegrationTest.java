package com.codegym.aiplanning.controller.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.config.BillingProperties;
import com.codegym.aiplanning.controller.billing.dto.CreditWalletResponse;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.billing.CreditLedgerEntry;
import com.codegym.aiplanning.entity.billing.CreditWallet;
import com.codegym.aiplanning.entity.billing.LedgerEntryType;
import com.codegym.aiplanning.entity.billing.LedgerReferenceType;
import com.codegym.aiplanning.entity.profile.UserProfile;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.billing.CreditLedgerEntryRepository;
import com.codegym.aiplanning.repository.billing.CreditWalletRepository;
import com.codegym.aiplanning.repository.profile.UserProfileRepository;
import com.codegym.aiplanning.service.billing.CreditWalletAtomicInitializer;
import com.codegym.aiplanning.service.billing.CreditWalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class BillingWalletIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private CreditWalletRepository creditWalletRepository;

    @Autowired
    private CreditLedgerEntryRepository creditLedgerEntryRepository;

    @Autowired
    private CreditWalletService creditWalletService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Test 1 (Happy Path): GET wallet lần đầu -> wallet created -> welcome bonus cấp theo config -> 1 ledger created
    @Test
    void test1_happyPath_firstGet_createsWalletAndGrantsWelcomeBonus() throws Exception {
        UserAccount user = createUser("wallet-happy-user");
        String token = login(user.getEmail());

        mockMvc.perform(get(ApiConstant.BILLING + ApiConstant.BILLING_WALLET)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableCredits").value(1000))
                .andExpect(jsonPath("$.data.reservedCredits").value(0))
                .andExpect(jsonPath("$.data.id").isNotEmpty());

        CreditWallet wallet = creditWalletRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(wallet.getAvailableCredits()).isEqualTo(1000L);
        assertThat(wallet.getReservedCredits()).isEqualTo(0L);

        List<CreditLedgerEntry> ledgers = creditLedgerEntryRepository.findByUserIdOrderByRecordedAtDesc(user.getId());
        assertThat(ledgers).hasSize(1);
        CreditLedgerEntry entry = ledgers.get(0);
        assertThat(entry.getEntryType()).isEqualTo(LedgerEntryType.WELCOME_BONUS);
        assertThat(entry.getAvailableDelta()).isEqualTo(1000L);
        assertThat(entry.getReservedDelta()).isEqualTo(0L);
        assertThat(entry.getAvailableBalanceAfter()).isEqualTo(1000L);
        assertThat(entry.getReservedBalanceAfter()).isEqualTo(0L);
        assertThat(entry.getReferenceType()).isEqualTo(LedgerReferenceType.ACCOUNT);
        assertThat(entry.getReferenceId()).isEqualTo(user.getId());
        assertThat(entry.getIdempotencyKey()).isEqualTo("WELCOME_BONUS:" + user.getId());
    }

    // Test 2 (Idempotent Lazy Init): GET wallet lần 2 -> không tạo thêm wallet -> không tạo bonus ledger -> balance không đổi
    @Test
    void test2_idempotentLazyInit_secondGet_doesNotDuplicateWalletOrLedger() throws Exception {
        UserAccount user = createUser("wallet-idempotent-user");
        String token = login(user.getEmail());

        // First call
        mockMvc.perform(get(ApiConstant.BILLING + ApiConstant.BILLING_WALLET)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableCredits").value(1000));

        // Second call
        mockMvc.perform(get(ApiConstant.BILLING + ApiConstant.BILLING_WALLET)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableCredits").value(1000))
                .andExpect(jsonPath("$.data.reservedCredits").value(0));

        CreditWallet wallet = creditWalletRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(wallet.getAvailableCredits()).isEqualTo(1000L);

        List<CreditLedgerEntry> ledgers = creditLedgerEntryRepository.findByUserIdOrderByRecordedAtDesc(user.getId());
        assertThat(ledgers).hasSize(1);
    }

    // Test 3 (Concurrency & Graceful Handling): Concurrent requests gặp duplicate-key được xử lý an toàn
    @Test
    void test3_concurrencyAndGracefulHandling_multipleConcurrentGets_returnsConsistentWallet() throws Exception {
        UserAccount user = createUser("wallet-concurrency-user");
        int numberOfThreads = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);

        List<Callable<CreditWalletResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < numberOfThreads; i++) {
            tasks.add(() -> creditWalletService.getOrCreateWallet(user.getId()));
        }

        List<Future<CreditWalletResponse>> futures = executorService.invokeAll(tasks);
        executorService.shutdown();

        UUID expectedWalletId = null;
        for (Future<CreditWalletResponse> future : futures) {
            CreditWalletResponse response = future.get();
            assertThat(response).isNotNull();
            assertThat(response.availableCredits()).isEqualTo(1000L);
            assertThat(response.reservedCredits()).isEqualTo(0L);
            if (expectedWalletId == null) {
                expectedWalletId = response.id();
            } else {
                assertThat(response.id()).isEqualTo(expectedWalletId);
            }
        }

        CreditWallet wallet = creditWalletRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(wallet.getId()).isEqualTo(expectedWalletId);
        assertThat(wallet.getAvailableCredits()).isEqualTo(1000L);

        List<CreditLedgerEntry> ledgers = creditLedgerEntryRepository.findByUserIdOrderByRecordedAtDesc(user.getId());
        assertThat(ledgers).hasSize(1);
    }

    // Test 4 (Zero config): Nếu config welcomeCredits = 0 -> tạo wallet (balance = 0) -> KHÔNG tạo ledger bonus
    @Test
    void test4_zeroConfig_createsWalletWithZeroBalanceAndNoLedger() {
        UserAccount user = createUser("wallet-zero-user");
        CreditWalletAtomicInitializer zeroInitializer = new CreditWalletAtomicInitializer(
                creditWalletRepository,
                creditLedgerEntryRepository,
                new BillingProperties(0L));

        CreditWallet wallet = zeroInitializer.createWalletWithWelcomeBonus(user);
        assertThat(wallet.getAvailableCredits()).isZero();
        assertThat(wallet.getReservedCredits()).isZero();

        List<CreditLedgerEntry> ledgers = creditLedgerEntryRepository.findByUserIdOrderByRecordedAtDesc(user.getId());
        assertThat(ledgers).isEmpty();
    }

    // Test 5 (DB Constraint): Cố tình save entity với available < 0 -> DB reject
    @Test
    void test5_dbConstraint_negativeAvailableCredits_rejected() {
        UserAccount user = createUser("wallet-check-available");
        CreditWallet invalidWallet = CreditWallet.createWithBalances(user, -10L, 0L);

        assertThatThrownBy(() -> creditWalletRepository.saveAndFlush(invalidWallet))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // Test 6 (DB Constraint): Cố tình save entity với reserved < 0 -> DB reject
    @Test
    void test6_dbConstraint_negativeReservedCredits_rejected() {
        UserAccount user = createUser("wallet-check-reserved");
        CreditWallet invalidWallet = CreditWallet.createWithBalances(user, 100L, -5L);

        assertThatThrownBy(() -> creditWalletRepository.saveAndFlush(invalidWallet))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // Test 7 (Owner Scope): Auth bằng USER A -> chỉ xem/khởi tạo được ví của USER A
    @Test
    void test7_ownerScope_userOnlyAccessesOwnWallet() throws Exception {
        UserAccount userA = createUser("wallet-owner-a");
        UserAccount userB = createUser("wallet-owner-b");

        String tokenA = login(userA.getEmail());
        String tokenB = login(userB.getEmail());

        MvcResult resultA = mockMvc.perform(get(ApiConstant.BILLING + ApiConstant.BILLING_WALLET)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();
        CreditWalletResponse respA = objectMapper.readValue(
                objectMapper.readTree(resultA.getResponse().getContentAsString()).path("data").toString(),
                CreditWalletResponse.class);

        MvcResult resultB = mockMvc.perform(get(ApiConstant.BILLING + ApiConstant.BILLING_WALLET)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn();
        CreditWalletResponse respB = objectMapper.readValue(
                objectMapper.readTree(resultB.getResponse().getContentAsString()).path("data").toString(),
                CreditWalletResponse.class);

        assertThat(respA.id()).isNotEqualTo(respB.id());

        CreditWallet walletA = creditWalletRepository.findByUserId(userA.getId()).orElseThrow();
        CreditWallet walletB = creditWalletRepository.findByUserId(userB.getId()).orElseThrow();

        assertThat(walletA.getId()).isEqualTo(respA.id());
        assertThat(walletB.getId()).isEqualTo(respB.id());
    }

    // Test 8 (Ledger Idempotency): Cố tình insert thủ công 2 ledger entry có cùng idempotency_key -> DB reject
    @Test
    void test8_ledgerIdempotency_duplicateIdempotencyKey_rejected() {
        UserAccount user = createUser("wallet-ledger-idem");
        CreditWallet wallet = creditWalletRepository.saveAndFlush(CreditWallet.create(user));

        String idempotencyKey = "WELCOME_BONUS:" + user.getId();
        CreditLedgerEntry firstEntry = CreditLedgerEntry.create(
                wallet,
                user,
                LedgerEntryType.WELCOME_BONUS,
                1000L,
                0L,
                1000L,
                0L,
                LedgerReferenceType.ACCOUNT,
                user.getId(),
                idempotencyKey,
                "First ledger entry");
        creditLedgerEntryRepository.saveAndFlush(firstEntry);

        CreditLedgerEntry duplicateEntry = CreditLedgerEntry.create(
                wallet,
                user,
                LedgerEntryType.WELCOME_BONUS,
                1000L,
                0L,
                1000L,
                0L,
                LedgerReferenceType.ACCOUNT,
                user.getId(),
                idempotencyKey,
                "Duplicate ledger entry");

        assertThatThrownBy(() -> creditLedgerEntryRepository.saveAndFlush(duplicateEntry))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UserAccount createUser(String alias) {
        UserAccount account = userAccountRepository.saveAndFlush(UserAccount.create(
                alias + "@example.com",
                passwordEncoder.encode("Password@123"),
                UserRole.USER,
                AccountStatus.ACTIVE));
        userProfileRepository.saveAndFlush(UserProfile.create(account, alias));
        return account;
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post(ApiConstant.AUTH_LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "Password@123"))))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();
    }
}
