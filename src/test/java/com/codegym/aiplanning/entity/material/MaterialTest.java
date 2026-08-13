package com.codegym.aiplanning.entity.material;

import com.codegym.aiplanning.entity.auth.AccountStatus;
import com.codegym.aiplanning.entity.auth.UserAccount;
import com.codegym.aiplanning.entity.auth.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaterialTest {

    private UserAccount user;

    @BeforeEach
    void setUp() {
        user = UserAccount.create(
                "testuser",
                "test@example.com",
                "hash",
                "Test User",
                UserRole.USER,
                AccountStatus.ACTIVE
        );
    }

    @Test
    void create_ShouldInitializeWithPendingStatus() {
        Material material = Material.create(user, "test.pdf", "application/pdf", 1024L, "s3://key");
        
        assertThat(material.getStatus()).isEqualTo(MaterialStatus.PENDING);
        assertThat(material.getType()).isEqualTo(MaterialType.FILE);
        assertThat(material.getContent()).isNull();
        assertThat(material.getErrorCode()).isNull();
    }

    @Test
    void markAsProcessing_FromPending_ShouldSucceed() {
        Material material = Material.create(user, "test.pdf", "application/pdf", 1024L, "s3://key");
        
        material.markAsProcessing();
        
        assertThat(material.getStatus()).isEqualTo(MaterialStatus.PROCESSING);
        assertThat(material.getProcessingStartedAt()).isNotNull();
    }

    @Test
    void markAsProcessing_FromInvalidState_ShouldThrowException() {
        Material material = Material.createText(user, MaterialType.TEXT, "Some text");
        // Created from text, status is READY immediately
        
        assertThatThrownBy(material::markAsProcessing)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only PENDING materials can be marked as PROCESSING");
    }

    @Test
    void markAsReady_FromProcessing_ShouldSucceedAndClearErrors() {
        Material material = Material.create(user, "test.pdf", "application/pdf", 1024L, "s3://key");
        material.markAsProcessing();
        
        material.markAsReady("Extracted Content");
        
        assertThat(material.getStatus()).isEqualTo(MaterialStatus.READY);
        assertThat(material.getContent()).isEqualTo("Extracted Content");
        assertThat(material.getErrorCode()).isNull();
        assertThat(material.getErrorMessage()).isNull();
    }

    @Test
    void markAsReady_FromPending_ShouldThrowException() {
        Material material = Material.create(user, "test.pdf", "application/pdf", 1024L, "s3://key");
        
        assertThatThrownBy(() -> material.markAsReady("Text"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only PROCESSING materials can be marked as READY");
    }

    @Test
    void markAsFailed_FromProcessing_ShouldSucceed() {
        Material material = Material.create(user, "test.pdf", "application/pdf", 1024L, "s3://key");
        material.markAsProcessing();
        
        material.markAsFailed(ExtractionErrorCode.PASSWORD_PROTECTED, "Password required");
        
        assertThat(material.getStatus()).isEqualTo(MaterialStatus.FAILED);
        assertThat(material.getErrorCode()).isEqualTo(ExtractionErrorCode.PASSWORD_PROTECTED);
        assertThat(material.getErrorMessage()).isEqualTo("Password required");
    }

    @Test
    void markAsFailed_WithNullCode_ShouldThrowException() {
        Material material = Material.create(user, "test.pdf", "application/pdf", 1024L, "s3://key");
        material.markAsProcessing();
        
        assertThatThrownBy(() -> material.markAsFailed(null, "Error"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void archive_FromReady_ShouldSetArchivedAt() {
        Material material = Material.createText(user, MaterialType.TEXT, "Some text");
        
        material.archive();
        
        assertThat(material.isArchived()).isTrue();
        assertThat(material.getArchivedAt()).isNotNull();
    }

    @Test
    void archive_FromPending_ShouldThrowException() {
        Material material = Material.create(user, "test.pdf", "application/pdf", 1024L, "s3://key");
        
        assertThatThrownBy(material::archive)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot archive a material that is PENDING or PROCESSING");
    }

    @Test
    void archive_WhenAlreadyArchived_ShouldBeIdempotent() {
        Material material = Material.createText(user, MaterialType.TEXT, "Some text");
        material.archive();
        java.time.Instant firstTime = material.getArchivedAt();
        
        // Wait a bit to ensure time would change if it wasn't idempotent
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        
        material.archive();
        
        assertThat(material.getArchivedAt()).isEqualTo(firstTime);
    }
}
