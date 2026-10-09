package com.codegym.aiplanning.controller.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codegym.aiplanning.common.constant.ApiConstant;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.billing.CreditLedgerEntry;
import com.codegym.aiplanning.entity.billing.CreditWallet;
import com.codegym.aiplanning.entity.billing.LedgerEntryType;
import com.codegym.aiplanning.entity.billing.LedgerReferenceType;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import com.codegym.aiplanning.repository.billing.CreditLedgerEntryRepository;
import com.codegym.aiplanning.repository.billing.CreditWalletRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class BillingTransactionsAndAiPricesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private CreditWalletRepository creditWalletRepository;

    @Autowired
    private CreditLedgerEntryRepository creditLedgerEntryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("GET /api/v1/billing/ai-prices returns seeded AI credit prices")
    void getAiPrices_returnsActiveRates() throws Exception {
        String token = registerAndLogin("user-rates-" + UUID.randomUUID() + "@example.com");

        mockMvc.perform(get(ApiConstant.BILLING + ApiConstant.BILLING_AI_PRICES)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("GET /api/v1/billing/transactions returns transactions with safe fields and filtering")
    void getTransactions_returnsPaginatedList() throws Exception {
        String email = "user-tx-" + UUID.randomUUID() + "@example.com";
        String token = registerAndLogin(email);

        UserAccount user = userAccountRepository.findByEmailIgnoreCase(email).orElseThrow();
        CreditWallet wallet = creditWalletRepository.findByUserId(user.getId()).orElseGet(() -> {
            CreditWallet created = CreditWallet.create(user);
            return creditWalletRepository.save(created);
        });

        UUID refId = UUID.randomUUID();
        CreditLedgerEntry entry = CreditLedgerEntry.create(
                wallet,
                user,
                LedgerEntryType.RELEASE_RESERVE,
                10L,
                -10L,
                1000L,
                0L,
                LedgerReferenceType.AI_EXECUTION,
                refId,
                "TEST_RELEASE:" + refId,
                "Hoàn lại credit giữ chỗ do thao tác AI không thành công");
        creditLedgerEntryRepository.save(entry);

        mockMvc.perform(get(ApiConstant.BILLING + ApiConstant.BILLING_TRANSACTIONS)
                        .param("entryType", "RELEASE_RESERVE")
                        .param("page", "0")
                        .param("size", "10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].entryType").value("RELEASE_RESERVE"))
                .andExpect(jsonPath("$.data.content[0].amount").value(10))
                .andExpect(jsonPath("$.data.content[0].availableDelta").value(10))
                .andExpect(jsonPath("$.data.content[0].reservedDelta").value(-10))
                .andExpect(jsonPath("$.data.content[0].status").value("RELEASED"))
                .andExpect(jsonPath("$.data.content[0].referenceType").value("AI_EXECUTION"))
                .andExpect(jsonPath("$.data.content[0].referenceId").value(refId.toString()))
                .andExpect(jsonPath("$.data.content[0].apiKey").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].secret").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].webhookSignature").doesNotExist());
    }

    private String registerAndLogin(String email) throws Exception {
        UserAccount account = UserAccount.create(
                email,
                passwordEncoder.encode("Password@123"),
                UserRole.USER,
                AccountStatus.ACTIVE);
        userAccountRepository.save(account);

        MvcResult loginResult = mockMvc.perform(post(ApiConstant.AUTH_LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "Password@123"))))
                .andExpect(status().isOk())
                .andReturn();

        var tree = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return tree.path("data").path("accessToken").asText();
    }
}
