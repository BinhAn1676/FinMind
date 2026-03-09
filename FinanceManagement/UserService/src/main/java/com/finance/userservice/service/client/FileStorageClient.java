package com.finance.userservice.service.client;

import com.finance.userservice.dto.file.FileDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(name = "files", path = "/api/v1/files")
public interface FileStorageClient {

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    FileDto upload(@RequestPart("userId") String userId,
                   @RequestPart("purpose") String purpose,
                   @RequestPart("file") MultipartFile file,
                   @RequestPart(value = "description", required = false) String description);
}


