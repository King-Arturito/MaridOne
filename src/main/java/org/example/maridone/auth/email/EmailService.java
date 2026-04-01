package org.example.maridone.auth.email;

import org.example.maridone.config.DefaultConfig;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
//todo: change to prod
public class EmailService {

    private final JavaMailSender mailSender;
    private final DefaultConfig defaultConfig;
    private final String frontendUrl;

    public EmailService(JavaMailSender mailSender, DefaultConfig defaultConfig) {
        this.mailSender = mailSender;
        this.defaultConfig = defaultConfig;
        this.frontendUrl = (defaultConfig.getUrl() == null || defaultConfig.getUrl().isBlank())
                ? "http://localhost:5173"
                : defaultConfig.getUrl();
    }

    public void sendVerificationEmail(String toEmail, String jwtToken) {

        String verifyUrl = frontendUrl + "/verify-email?token=" + jwtToken;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Verify your MaridOne Account");
        message.setText("Welcome to MaridOne!\n\n" +
                "Please click the link below to verify your email address. " +
                "This link will expire in 24 hours.\n\n" +
                verifyUrl + "\n\n" +
                "If you did not request this, please ignore this email.");

        mailSender.send(message);
    }
}