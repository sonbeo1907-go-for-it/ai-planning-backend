package com.codegym.aiplanning.service.email;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.codegym.aiplanning.service.auth.model.PasswordResetRequestedEvent;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailListenerTest {

    @Mock
    private EmailService emailService;

    private EmailListener emailListener;

    @BeforeEach
    void setUp() {
        emailListener = new EmailListener(emailService);
    }

    @Test
    void handlePasswordResetRequested_WhenSuccessful_CallsEmailService() {
        // Arrange
        String email = "test@example.com";
        String resetLink = "http://localhost:3000/reset?token=123";
        PasswordResetRequestedEvent event = new PasswordResetRequestedEvent(email, resetLink, Instant.now());

        // Act
        emailListener.handlePasswordResetRequested(event);

        // Assert
        verify(emailService).sendPasswordResetEmail(email, resetLink);
    }

    @Test
    void handlePasswordResetRequested_WhenFails_RethrowsExceptionForRetryable() {
        // Arrange
        String email = "test@example.com";
        String resetLink = "http://localhost:3000/reset?token=123";
        PasswordResetRequestedEvent event = new PasswordResetRequestedEvent(email, resetLink, Instant.now());
        
        RuntimeException mockException = new RuntimeException("SMTP Server Down");
        doThrow(mockException).when(emailService).sendPasswordResetEmail(email, resetLink);

        // Act & Assert
        RuntimeException thrown = assertThrows(
                RuntimeException.class, 
                () -> emailListener.handlePasswordResetRequested(event)
        );
        
        // Ensure the exception is re-thrown so @Retryable can catch it
        verify(emailService).sendPasswordResetEmail(email, resetLink);
    }
}
