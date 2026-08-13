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
import java.util.Collections;
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

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private UUID testMaterialId;

    @BeforeEach
    // Sets up the data but commits after BeforeEach? No, @Transactional on test rolls back.
    // For concurrency tests, we usually need data to be committed so other threads can see it.
    // Therefore, we shouldn't use @Transactional on the test method itself.
    void setUp() {
        // Clean up
        jdbcTemplate.execute("DELETE FROM refresh_tokens");
        jdbcTemplate.execute("DELETE FROM auth_sessions");
        jdbcTemplate.execute("DELETE FROM auth_identities");
        materialRepository.deleteAll();
        userAccountRepository.deleteAll();

        UserAccount user = UserAccount.create("concurrentuser", "concurrent@test.com", "hash", "User", UserRole.USER, AccountStatus.ACTIVE);
        userAccountRepository.save(user);

        Material material = Material.create(user, "test.pdf", "application/pdf", 1024L, "s3://key");
        materialRepository.save(material);
        
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
}
