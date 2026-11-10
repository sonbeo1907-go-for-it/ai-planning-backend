package com.codegym.aiplanning.repository;

import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import com.codegym.aiplanning.entity.material.Material;
import com.codegym.aiplanning.entity.material.MaterialStatus;
import com.codegym.aiplanning.repository.auth.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MaterialRepositoryConcurrencyIntegrationTest {

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private UUID testMaterialId;

    @BeforeEach
    void setUp() {
        UserAccount user = UserAccount.create(
                "concurrent-" + UUID.randomUUID() + "@test.com",
                "hash",
                UserRole.USER,
                AccountStatus.ACTIVE);
        userAccountRepository.saveAndFlush(user);

        Material material = Material.create(
                user,
                "test.pdf",
                "application/pdf",
                1024L,
                "test/" + UUID.randomUUID() + ".pdf");
        materialRepository.saveAndFlush(material);

        testMaterialId = material.getId();
    }

    @Test
    void claimForProcessing_ShouldOnlyAllowOneThreadToClaim() throws InterruptedException {
        int numberOfThreads = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);

        List<Callable<Integer>> tasks = new ArrayList<>();

        for (int i = 0; i < numberOfThreads; i++) {
            tasks.add(() -> {
                // Each thread attempts to claim the same material within its own transaction
                return transactionTemplate.execute(status -> {
                    return materialRepository.claimForProcessing(testMaterialId, java.time.Instant.now());
                });
            });
        }

        // Execute all tasks concurrently
        List<Future<Integer>> futures = executorService.invokeAll(tasks);

        int totalSuccessfulClaims = 0;
        for (Future<Integer> future : futures) {
            try {
                int updatedRows = future.get();
                if (updatedRows == 1) {
                    totalSuccessfulClaims++;
                }
            } catch (Exception e) {
                e.printStackTrace();
                // Ignore execution exceptions
            }
        }

        executorService.shutdown();

        // Only exactly one thread should have successfully claimed it
        assertThat(totalSuccessfulClaims).isEqualTo(1);

        // Verify final state
        Material material = materialRepository.findById(testMaterialId).orElseThrow();
        assertThat(material.getStatus()).isEqualTo(MaterialStatus.PROCESSING);
        assertThat(material.getProcessingStartedAt()).isNotNull();
    }

    @Test
    void claimForProcessing_ShouldIgnoreArchivedMaterial() {
        Material material = materialRepository.findById(testMaterialId).orElseThrow();
        material.archive();
        materialRepository.saveAndFlush(material);

        int updatedRows = transactionTemplate.execute(status ->
                materialRepository.claimForProcessing(testMaterialId, java.time.Instant.now()));

        assertThat(updatedRows).isZero();
        assertThat(materialRepository.findById(testMaterialId).orElseThrow().getStatus())
                .isEqualTo(MaterialStatus.PENDING);
    }
}
