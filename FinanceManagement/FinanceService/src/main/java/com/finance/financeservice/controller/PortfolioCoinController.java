package com.finance.financeservice.controller;

import com.finance.financeservice.mongo.document.PortfolioCoin;
import com.finance.financeservice.service.PortfolioCoinService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/portfolio-coins")
@RequiredArgsConstructor
public class PortfolioCoinController {

    private final PortfolioCoinService portfolioCoinService;

    @GetMapping
    public ResponseEntity<List<PortfolioCoin>> findByUserId(@RequestParam String userId) {
        return ResponseEntity.ok(portfolioCoinService.findByUserId(userId));
    }

    @PostMapping
    public ResponseEntity<PortfolioCoin> addCoin(@RequestBody PortfolioCoin coin) {
        return ResponseEntity.ok(portfolioCoinService.addCoin(coin));
    }

    @DeleteMapping
    public ResponseEntity<Void> removeCoin(@RequestParam String userId, @RequestParam String coinId) {
        portfolioCoinService.removeCoin(userId, coinId);
        return ResponseEntity.noContent().build();
    }
}
