package com.codegym.aiplanning.service.email;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

@ExtendWith(MockitoExtension.class)
class SmtpEmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private ITemplateEngine templateEngine;

    private SmtpEmailService smtpEmailService;

    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() {
        smtpEmailService = new SmtpEmailService(mailSender, templateEngine, "test@resend.dev");
        mimeMessage = mock(MimeMessage.class);
    }

    @Test
    void sendPasswordResetEmail_Success() {
        // Arrange
        String toEmail = "test@example.com";
        String resetLink = "http://localhost:3000/reset?token=123";
        String htmlContent = "<html>Mock HTML</html>";

        when(templateEngine.process(eq("password-reset"), any(Context.class))).thenReturn(htmlContent);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act
        smtpEmailService.sendPasswordResetEmail(toEmail, resetLink);

        // Assert
        verify(templateEngine).process(eq("password-reset"), any(Context.class));
        verify(mailSender).createMimeMessage();
        verify(mailSender).send(mimeMessage);
    }
}
