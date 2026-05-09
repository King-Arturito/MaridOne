package org.example.maridone.auth.email;

import org.example.maridone.config.DefaultConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.net.URI;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Test
    void sendVerificationEmail_ShouldUseBackendVerificationEndpoint() {
        DefaultConfig config = new DefaultConfig();
        config.setUrl(URI.create("http://localhost:5173"));
        config.setApiUrl(URI.create("http://localhost:8080"));

        EmailService emailService = new EmailService(mailSender, config);

        emailService.sendVerificationEmail("person@example.com", "sample-token");

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage message = messageCaptor.getValue();
        Assertions.assertArrayEquals(new String[] { "person@example.com" }, message.getTo());
        Assertions.assertTrue(message.getText().contains("http://localhost:8080/api/auth/verify-email?token=sample-token"));
    }
}
