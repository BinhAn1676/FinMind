package com.finance.userservice.controller;

import com.finance.userservice.constant.UserConstants;
import com.finance.userservice.dto.ResponseDto;
import com.finance.userservice.dto.user.UserContactDto;
import com.finance.userservice.dto.user.UserDto;
import com.finance.userservice.dto.properties.UsersContactInfoDto;
import com.finance.userservice.service.UserService;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;

import java.util.Map;

@RestController
@RequestMapping(path = "/api/v1/users")
@Validated
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final UsersContactInfoDto usersContactInfoDto;

    @PostMapping("/test-body")
    public ResponseEntity<?> test(@RequestHeader("finance-trace-id") String traceId) {
        System.out.println("Trace ID: " + traceId);
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/search/contact")
    public ResponseEntity<Page<UserContactDto>> searchContacts(@RequestParam(value = "textSearch", required = false) String textSearch,
                                                               @RequestParam(value = "page", defaultValue = "0") int page,
                                                               @RequestParam(value = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(userService.searchByContact(textSearch, page, size));
    }

    @PostMapping("/contact-info")
    public ResponseEntity<?> getContactInfo() {
        Map<String, Object> response = Map.of(
                "message", usersContactInfoDto.getMessage(),
                "contactDetails", usersContactInfoDto.getContactDetails(),
                "onCallSupport", usersContactInfoDto.getOnCallSupport()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody @Validated UserDto user) {
        return ResponseEntity.ok(userService.create(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable("id")  Long id) {
        var user = userService.getById(id);
        return ResponseEntity.status(HttpStatus.OK).body(user);
    }

    @GetMapping("/username/{username}")
    public ResponseEntity<?> getByUsername(@PathVariable("username") @NotEmpty(message = "Username can not be empty or null") String username) {
        var user = userService.getByUsername(username);
        return ResponseEntity.status(HttpStatus.OK).body(user);
    }

    @PostMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable("id") Long id, @RequestBody @Validated UserDto user) {
        return ResponseEntity.ok(userService.update(id, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable("id") @NotEmpty(message = "Id can not be empty or null") Long id) {
        boolean isDeleted = userService.delete(id);
        if (isDeleted) {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(new ResponseDto(UserConstants.STATUS_200, UserConstants.MESSAGE_200));
        } else {
            return ResponseEntity
                    .status(HttpStatus.EXPECTATION_FAILED)
                    .body(new ResponseDto(UserConstants.STATUS_417, UserConstants.MESSAGE_417_DELETE));
        }
    }


    @GetMapping()
    public ResponseEntity<?> getUsers(@RequestParam(value = "textSearch", required = false) String textSearch,
                                      @RequestParam(value = "page", defaultValue = "0") int page,
                                      @RequestParam(value = "size", defaultValue = "10") int size) {
        var users = userService.getUsers(textSearch, page, size);
        return ResponseEntity.status(HttpStatus.OK).body(users);

    }

    @GetMapping("/user-info")
    public ResponseEntity<?> getUserInfo() {
        var user = userService.getUserInfoFromToken();
        return ResponseEntity.status(HttpStatus.OK).body(user);
    }

    @PutMapping("/{id}/bank-token")
    public ResponseEntity<?> updateBankToken(@PathVariable("id") Long id, @RequestBody Map<String, String> request) {
        String bankToken = request.get("bankToken");
        if (bankToken == null || bankToken.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new ResponseDto("400", "Bank token is required"));
        }
        
        var updatedUser = userService.updateBankToken(id, bankToken);
        return ResponseEntity.ok(updatedUser);
    }
}