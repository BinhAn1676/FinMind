package com.finance.financeservice.service.impl;

import com.finance.financeservice.dto.PageResponse;
import com.finance.financeservice.mongo.document.InvestmentLot;
import com.finance.financeservice.mongo.repository.InvestmentLotRepository;
import com.finance.financeservice.service.InvestmentLotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvestmentLotServiceImpl implements InvestmentLotService {

    private final InvestmentLotRepository investmentLotRepository;
    private final MongoTemplate mongoTemplate;

    @Override
    public List<InvestmentLot> findByUserId(String userId) {
        return investmentLotRepository.findByUserIdOrderByBuyDateDesc(userId);
    }

    @Override
    public PageResponse<InvestmentLot> findByUserIdFiltered(
            String userId, String symbol, String dateFrom, String dateTo, int page, int size) {

        Query query = new Query();
        List<Criteria> andList = new ArrayList<>();
        andList.add(Criteria.where("user_id").is(userId));

        if (symbol != null && !symbol.isBlank()) {
            andList.add(Criteria.where("symbol").regex(symbol.trim(), "i"));
        }

        Criteria dateCriteria = null;
        if (dateFrom != null && !dateFrom.isBlank() && dateTo != null && !dateTo.isBlank()) {
            dateCriteria = Criteria.where("buy_date")
                    .gte(LocalDate.parse(dateFrom))
                    .lte(LocalDate.parse(dateTo));
        } else if (dateFrom != null && !dateFrom.isBlank()) {
            dateCriteria = Criteria.where("buy_date").gte(LocalDate.parse(dateFrom));
        } else if (dateTo != null && !dateTo.isBlank()) {
            dateCriteria = Criteria.where("buy_date").lte(LocalDate.parse(dateTo));
        }
        if (dateCriteria != null) andList.add(dateCriteria);

        query.addCriteria(new Criteria().andOperator(andList.toArray(new Criteria[0])));
        query.with(Sort.by(Sort.Direction.DESC, "buy_date"));

        long total = mongoTemplate.count(query, InvestmentLot.class);

        query.skip((long) page * size).limit(size);
        List<InvestmentLot> content = mongoTemplate.find(query, InvestmentLot.class);

        int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
        return new PageResponse<>(content, total, totalPages, page, size);
    }

    @Override
    public InvestmentLot create(InvestmentLot lot) {
        lot.setCreatedAt(LocalDateTime.now());
        lot.setUpdatedAt(LocalDateTime.now());
        return investmentLotRepository.save(lot);
    }

    @Override
    public InvestmentLot update(String id, InvestmentLot update) {
        return investmentLotRepository.findById(id).map(existing -> {
            if (update.getAssetType() != null) existing.setAssetType(update.getAssetType());
            if (update.getSymbol() != null) existing.setSymbol(update.getSymbol());
            if (update.getName() != null) existing.setName(update.getName());
            if (update.getBuyDate() != null) existing.setBuyDate(update.getBuyDate());
            if (update.getQuantity() != null) existing.setQuantity(update.getQuantity());
            if (update.getBuyPriceVnd() != null) existing.setBuyPriceVnd(update.getBuyPriceVnd());
            if (update.getNote() != null) existing.setNote(update.getNote());
            if (update.getTransactionType() != null) existing.setTransactionType(update.getTransactionType());
            if (update.getFees() != null) existing.setFees(update.getFees());
            existing.setUpdatedAt(LocalDateTime.now());
            return investmentLotRepository.save(existing);
        }).orElseThrow(() -> new IllegalArgumentException("InvestmentLot not found: " + id));
    }

    @Override
    public void delete(String id) {
        investmentLotRepository.deleteById(id);
    }

    @Override
    public void deleteByUserIdAndSymbol(String userId, String symbol) {
        List<InvestmentLot> lots = investmentLotRepository.findByUserIdAndSymbol(userId, symbol);
        investmentLotRepository.deleteAll(lots);
    }
}
