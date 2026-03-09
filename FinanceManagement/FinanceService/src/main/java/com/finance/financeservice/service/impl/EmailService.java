package com.finance.financeservice.service.impl;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "spring.mail.host")
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendReportEmail(String recipientEmail, byte[] reportData, String fileName, String format) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(recipientEmail);
            helper.setSubject("Your Transaction Report - " + fileName);
            helper.setText(buildEmailBody(format), true);

            // Attach the report
            helper.addAttachment(fileName, new ByteArrayResource(reportData));

            mailSender.send(message);
            log.info("✅ Email sent successfully to: {}", recipientEmail);

        } catch (MessagingException e) {
            log.error("❌ Failed to send email to: {}", recipientEmail, e);
            throw new RuntimeException("Failed to send email: " + e.getMessage());
        }
    }

    private String buildEmailBody(String format) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body {
                        font-family: Arial, sans-serif;
                        line-height: 1.6;
                        color: #333;
                        max-width: 600px;
                        margin: 0 auto;
                        padding: 20px;
                    }
                    .header {
                        background: linear-gradient(135deg, #4ECDC4, #45B7D1);
                        color: white;
                        padding: 30px;
                        text-align: center;
                        border-radius: 10px 10px 0 0;
                    }
                    .content {
                        background: #f8f9fa;
                        padding: 30px;
                        border-radius: 0 0 10px 10px;
                    }
                    .button {
                        display: inline-block;
                        padding: 12px 24px;
                        background: #4ECDC4;
                        color: white;
                        text-decoration: none;
                        border-radius: 5px;
                        margin-top: 20px;
                    }
                    .footer {
                        text-align: center;
                        margin-top: 20px;
                        color: #666;
                        font-size: 12px;
                    }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>📊 Transaction Report</h1>
                    <p>Your financial report is ready</p>
                </div>
                <div class="content">
                    <p>Hello,</p>
                    <p>Your transaction report has been generated successfully and is attached to this email.</p>
                    <p><strong>Report Format:</strong> %s</p>
                    <p>The attached file contains a detailed summary of your transactions based on the filters you specified.</p>
                    <p>If you have any questions or need assistance, please don't hesitate to contact our support team.</p>
                    <p>Best regards,<br><strong>Finance Management Team</strong></p>
                </div>
                <div class="footer">
                    <p>This is an automated email. Please do not reply to this message.</p>
                </div>
            </body>
            </html>
            """.formatted(format.toUpperCase());
    }
}
