package com.finance.notificationservice.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface NotifyService {
    /**
     * Send an email
     */
    void sendEmail(String to, String subject, String message);
    

}
