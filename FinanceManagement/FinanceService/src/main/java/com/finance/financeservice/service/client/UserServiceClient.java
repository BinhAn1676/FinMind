package com.finance.financeservice.service.client;

import com.finance.financeservice.dto.PageResponse;
import com.finance.financeservice.dto.user.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "users")
public interface UserServiceClient {

    @GetMapping("/api/v1/users")
    PageResponse<UserDto> getUsers(@RequestParam("page") int page,
                                   @RequestParam("size") int size,
                                   @RequestParam(value = "textSearch", required = false) String textSearch);

    @GetMapping("/api/v1/users/{id}")
    UserDto getById(@PathVariable("id") Long id);
}


