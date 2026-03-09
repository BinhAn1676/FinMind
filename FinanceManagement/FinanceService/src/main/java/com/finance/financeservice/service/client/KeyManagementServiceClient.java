package com.finance.financeservice.service.client;

import com.finance.financeservice.dto.keys.DecryptRequest;
import com.finance.financeservice.dto.keys.DecryptResponse;
import com.finance.financeservice.dto.keys.EncryptRequest;
import com.finance.financeservice.dto.keys.EncryptResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "keys")
public interface KeyManagementServiceClient {

    @PostMapping("/api/v1/keys/decrypt")
    DecryptResponse decrypt(@RequestBody DecryptRequest request);

    @PostMapping("/api/v1/keys/encrypt")
    EncryptResponse encrypt(@RequestBody EncryptRequest request);
}


