package org.example.maridone.auth.email;

import org.example.maridone.config.DefaultConfig;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
//todo: change to prod
public class EmailService {

    private final JavaMailSender mailSender;
    private final String apiUrl;

    public EmailService(JavaMailSender mailSender, DefaultConfig defaultConfig) {
        this.mailSender = mailSender;
        this.apiUrl = defaultConfig.getApiUrlOrDefault();
    }

    public void sendVerificationEmail(String toEmail, String jwtToken) {
        String verifyUrl = UriComponentsBuilder.fromUriString(apiUrl)
                .path("/api/auth/verify-email")
                .queryParam("token", jwtToken)
                .build()
                .toUriString();
        sendPlainTextEmail(
                toEmail,
                "Verify your MaridOne Email Address",
                "Please click the link below to verify your email address. " +
                        "This link will expire in 24 hours.\n\n" +
                        verifyUrl + "\n\n" +
                        "If you did not request this, please ignore this email."
        );
    }

    public void sendVerificationCode(String email, String tokenToSend) {
        sendPlainTextEmail(
                email,
                "Two Factor Authentication",
                "2FA code: " + tokenToSend +"\n\nThis code expires in 30 seconds."
        );
    }

    public void sendPasswordResetCode(String email, String tokenToSend) {
        sendPlainTextEmail(
                email,
                "MaridOne Password Reset Code",
                "Use this 6-digit code to reset your password: " + tokenToSend
                        + "\n\nThis code expires in 10 minutes."
                        + "\n\nIf you did not request a password reset, you can ignore this email."
        );
    }

    public void sendPlainTextEmail(String email, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
