package com.finance.financeservice.controller;

import com.finance.financeservice.dto.PageResponse;
import com.finance.financeservice.mongo.document.InvestmentLot;
import com.finance.financeservice.service.InvestmentLotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/investment-lots")
@RequiredArgsConstructor
public class InvestmentLotController {

    private final InvestmentLotService investmentLotService;

    @GetMapping
    public ResponseEntity<List<InvestmentLot>> findByUserId(@RequestParam String userId) {
        return ResponseEntity.ok(investmentLotService.findByUserId(userId));
    }

    @GetMapping("/paged")
    public ResponseEntity<PageResponse<InvestmentLot>> findPaged(
            @RequestParam String userId,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(investmentLotService.findByUserIdFiltered(userId, symbol, dateFrom, dateTo, page, size));
    }

    @PostMapping
    public ResponseEntity<InvestmentLot> create(@RequestBody InvestmentLot lot) {
        return ResponseEntity.ok(investmentLotService.create(lot));
    }

    @PutMapping("/{id}")
    public ResponseEntity<InvestmentLot> update(@PathVariable String id, @RequestBody InvestmentLot lot) {
        try {
            return ResponseEntity.ok(investmentLotService.update(id, lot));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        investmentLotService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/by-symbol")
    public ResponseEntity<Void> deleteBySymbol(@RequestParam String userId, @RequestParam String symbol) {
        investmentLotService.deleteByUserIdAndSymbol(userId, symbol);
        return ResponseEntity.noContent().build();
    }
}
