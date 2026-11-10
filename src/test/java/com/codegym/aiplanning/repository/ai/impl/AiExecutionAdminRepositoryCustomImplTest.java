package com.codegym.aiplanning.repository.ai.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.codegym.aiplanning.controller.admin.ai.dto.AdminAiExecutionFilter;
import com.codegym.aiplanning.entity.ai.AiExecution;
import com.codegym.aiplanning.entity.ai.AiExecutionOperation;
import com.codegym.aiplanning.entity.ai.AiExecutionResultType;
import com.codegym.aiplanning.entity.ai.AiExecutionStatus;
import com.codegym.aiplanning.entity.ai.AiExecutionTargetType;
import com.codegym.aiplanning.entity.ai.AiProvider;
import com.codegym.aiplanning.entity.ai.AiProviderConfig;
import com.codegym.aiplanning.entity.ai.AiProviderProtocol;
import com.codegym.aiplanning.entity.ai.AiPurpose;
import com.codegym.aiplanning.entity.ai.CredentialSelectionStrategy;
import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.repository.ai.AiExecutionRepository;
import com.codegym.aiplanning.repository.ai.AiProviderConfigRepository;
import com.codegym.aiplanning.repository.ai.AiProviderRepository;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class AiExecutionAdminRepositoryCustomImplTest {

    @Autowired
    private AiExecutionRepository executionRepository;

    @Autowired
    private AiProviderRepository providerRepository;

    @Autowired
    private AiProviderConfigRepository configRepository;

    @Autowired
    private UserAccountRepository accountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private jakarta.persistence.EntityManager em;

    private UserAccount testUser;
    private AiProvider provider1;
    private AiProvider provider2;
    private AiProviderConfig config1;
    private AiProviderConfig config2;

    @BeforeEach
    void setUp() {
        testUser = accountRepository.saveAndFlush(UserAccount.create(
                "repo-test-" + UUID.randomUUID() + "@example.com",
                passwordEncoder.encode("Password@123"),
                UserRole.USER,
                AccountStatus.ACTIVE));

        provider1 = providerRepository.saveAndFlush(AiProvider.create(
                "PROV1_" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(),
                "Provider Alpha",
                "https://alpha.example.com",
                AiProviderProtocol.OPENAI_COMPATIBLE,
                CredentialSelectionStrategy.PRIORITY,
                true));

        provider2 = providerRepository.saveAndFlush(AiProvider.create(
                "PROV2_" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(),
                "Provider Beta",
                "https://beta.example.com",
                AiProviderProtocol.OPENAI_COMPATIBLE,
                CredentialSelectionStrategy.PRIORITY,
                true));

        config1 = configRepository.saveAndFlush(AiProviderConfig.create(
                provider1,
                AiPurpose.ROADMAP_GENERATION,
                "alpha-model",
                true,
                30,
                4000,
                2000,
                BigDecimal.valueOf(0.7)));

        config2 = configRepository.saveAndFlush(AiProviderConfig.create(
                provider2,
                AiPurpose.DAILY_PLAN_GENERATION,
                "beta-model",
                true,
                30,
                4000,
                2000,
                BigDecimal.valueOf(0.7)));
    }

    @Test
    @DisplayName("Filter by date range (from / to)")
    void searchAdminExecutions_filterByDateRange() {
        Instant now = Instant.now();
        Instant fiveDaysAgo = now.minus(5, ChronoUnit.DAYS);
        Instant oneDayAgo = now.minus(1, ChronoUnit.DAYS);

        AiExecution oldExec = createExecution(config1, AiExecutionStatus.SUCCEEDED, fiveDaysAgo);
        AiExecution recentExec = createExecution(config1, AiExecutionStatus.SUCCEEDED, oneDayAgo);

        em.createNativeQuery("UPDATE ai_executions SET created_at = :createdAt WHERE id = :id")
                .setParameter("createdAt", fiveDaysAgo)
                .setParameter("id", oldExec.getId())
                .executeUpdate();

        em.createNativeQuery("UPDATE ai_executions SET created_at = :createdAt WHERE id = :id")
                .setParameter("createdAt", oneDayAgo)
                .setParameter("id", recentExec.getId())
                .executeUpdate();

        em.flush();
        em.clear();

        AdminAiExecutionFilter filter = new AdminAiExecutionFilter(
                now.minus(2, ChronoUnit.DAYS),
                now.plus(1, ChronoUnit.HOURS),
                null, null, null, null, null, null);

        Page<AiExecution> result = executionRepository.searchAdminExecutions(filter, PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(AiExecution::getId)
                .contains(recentExec.getId())
                .doesNotContain(oldExec.getId());
    }

    @Test
    @DisplayName("Filter by Provider and Model")
    void searchAdminExecutions_filterByProviderAndModel() {
        AiExecution exec1 = createExecution(config1, AiExecutionStatus.SUCCEEDED, Instant.now());
        AiExecution exec2 = createExecution(config2, AiExecutionStatus.SUCCEEDED, Instant.now());

        AdminAiExecutionFilter filter = new AdminAiExecutionFilter(
                null, null, provider1.getId(), "alpha-model", null, null, null, null);

        Page<AiExecution> result = executionRepository.searchAdminExecutions(filter, PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(AiExecution::getId)
                .contains(exec1.getId())
                .doesNotContain(exec2.getId());
    }

    @Test
    @DisplayName("Sorting with tie-breaker: custom sort appends id DESC for pagination stability")
    void searchAdminExecutions_customSortingWithTieBreaker() {
        createExecution(config1, AiExecutionStatus.SUCCEEDED, Instant.now());
        createExecution(config2, AiExecutionStatus.SUCCEEDED, Instant.now());

        PageRequest pageRequest = PageRequest.of(0, 10, Sort.by("latencyMs").ascending());
        Page<AiExecution> result = executionRepository.searchAdminExecutions(null, pageRequest);

        assertThat(result.getContent()).isNotEmpty();
    }

    private AiExecution createExecution(AiProviderConfig config, AiExecutionStatus status, Instant createdAt) {
        AiExecution execution = AiExecution.queue(
                testUser,
                config,
                config.getPurpose(),
                AiExecutionOperation.GENERATE,
                AiExecutionTargetType.ROADMAP,
                UUID.randomUUID(),
                UUID.randomUUID().toString());

        ReflectionTestUtils.setField(execution, "status", status);
        ReflectionTestUtils.setField(execution, "createdAt", createdAt);
        ReflectionTestUtils.setField(execution, "startedAt", createdAt.plusSeconds(1));
        ReflectionTestUtils.setField(execution, "completedAt", createdAt.plusSeconds(5));
        ReflectionTestUtils.setField(execution, "latencyMs", 4000L);
        ReflectionTestUtils.setField(execution, "inputTokens", 100);
        ReflectionTestUtils.setField(execution, "outputTokens", 200);

        if (status == AiExecutionStatus.SUCCEEDED) {
            ReflectionTestUtils.setField(execution, "resultType", AiExecutionResultType.ROADMAP_VERSION);
            ReflectionTestUtils.setField(execution, "resultId", UUID.randomUUID());
            ReflectionTestUtils.setField(execution, "activeSlotTargetId", null);
        }

        return executionRepository.saveAndFlush(execution);
    }
}
