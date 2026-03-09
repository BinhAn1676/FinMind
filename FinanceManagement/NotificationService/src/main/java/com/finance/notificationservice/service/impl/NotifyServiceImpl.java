package com.finance.notificationservice.service.impl;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.notificationservice.repository.NotificationRepository;
import com.finance.notificationservice.service.NotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotifyServiceImpl implements NotifyService {
    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void sendEmail(String to, String subject, String message) {
        log.info("Sending email to: {}, subject: {}", to, subject);
        log.debug("Email content: {}", message);
    }


}
