package com.finance.keymanagementservice;

import com.finance.keymanagementservice.service.GoogleCloudKmsService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@SpringBootTest
class KeyManagementServiceApplicationTests {

    // Provide a mock so the context can start without real KMS
    @MockBean
    private GoogleCloudKmsService googleCloudKmsService;
    
    // Mock JwtDecoder for security configuration
    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void contextLoads() {
    }

}
