package com.finance.notificationservice.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.notificationservice.repository.NotificationRepository;
import com.finance.notificationservice.service.NotifyService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotifyServiceImpl implements NotifyService {

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;
    private final JavaMailSender mailSender;

    @Value("${app.mail.from:noreply@finmind.vn}")
    private String fromAddress;

    @Value("${app.mail.from-name:FinMind - Finance Management}")
    private String fromName;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    @Override
    public void sendEmail(String to, String subject, String htmlContent) {
        if (!mailEnabled) {
            log.info("[Mail disabled] Would send to: {}, subject: {}", to, subject);
            return;
        }
        if (to == null || to.isBlank()) {
            log.warn("sendEmail called with empty recipient, skipping");
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Email sent successfully to: {}, subject: {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
