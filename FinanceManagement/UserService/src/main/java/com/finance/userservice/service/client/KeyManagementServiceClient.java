package com.finance.userservice.service.client;

import com.finance.userservice.dto.KeyEncryptRequest;
import com.finance.userservice.dto.KeyEncryptResponse;
import com.finance.userservice.dto.KeyDecryptRequest;
import com.finance.userservice.dto.KeyDecryptResponse;
import com.finance.userservice.dto.KeyGenerateRequest;
import com.finance.userservice.dto.KeyGenerateResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "keys")
public interface KeyManagementServiceClient {
    
    @PostMapping("/api/v1/keys/encrypt")
    KeyEncryptResponse encrypt(@RequestBody KeyEncryptRequest request);

    @PostMapping("/api/v1/keys/decrypt")
    KeyDecryptResponse decrypt(@RequestBody KeyDecryptRequest request);

    @PostMapping("/api/v1/keys/generate")
    KeyGenerateResponse generate(@RequestBody KeyGenerateRequest request);
}

