package com.finance.financeservice.service.impl;

import com.finance.financeservice.mongo.document.Transaction;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CSVExportService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final NumberFormat CURRENCY_FORMATTER = new DecimalFormat("#,###");

    public byte[] generateCsvFile(List<Transaction> transactions, String sortOrder, List<String> selectedColumns) throws IOException {
        // Sort transactions
        transactions = sortTransactions(transactions, sortOrder);

        // Column mapping
        Map<String, String> columnHeaders = new HashMap<>();
        columnHeaders.put("date", "Transaction Date");
        columnHeaders.put("type", "Type");
        columnHeaders.put("amount", "Amount");
        columnHeaders.put("category", "Category");
        columnHeaders.put("content", "Description");
        columnHeaders.put("account", "Account");
        columnHeaders.put("reference", "Reference Number");

        // Default columns if none specified
        List<String> columnsToShow = (selectedColumns == null || selectedColumns.isEmpty())
                ? List.of("date", "type", "amount", "category", "content", "account", "reference")
                : selectedColumns;

        // Build header array
        String[] headers = columnsToShow.stream()
                .map(col -> columnHeaders.getOrDefault(col, col))
                .toArray(String[]::new);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);

        // Add BOM for Excel compatibility with UTF-8
        outputStream.write(0xEF);
        outputStream.write(0xBB);
        outputStream.write(0xBF);

        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader(headers)
                .build();

        try (CSVPrinter csvPrinter = new CSVPrinter(writer, csvFormat)) {
            // Write data rows
            for (Transaction transaction : transactions) {
                List<String> values = new ArrayList<>();

                for (String column : columnsToShow) {
                    String value = "";

                    switch (column) {
                        case "date":
                            if (transaction.getTransactionDate() != null) {
                                value = transaction.getTransactionDate().format(DATE_FORMATTER);
                            }
                            break;

                        case "type":
                            value = transaction.getTransactionType() != null ? transaction.getTransactionType() : "";
                            break;

                        case "amount":
                            double amount = 0;
                            if (transaction.getAmountIn() != null && transaction.getAmountIn() > 0) {
                                amount = transaction.getAmountIn();
                            } else if (transaction.getAmountOut() != null && transaction.getAmountOut() > 0) {
                                amount = -transaction.getAmountOut();
                            }
                            value = amount != 0 ? CURRENCY_FORMATTER.format(amount) : "0";
                            break;

                        case "category":
                            value = transaction.getCategory() != null ? transaction.getCategory() : "N/A";
                            break;

                        case "content":
                            value = transaction.getTransactionContent() != null ? transaction.getTransactionContent() : "";
                            break;

                        case "account":
                            if (transaction.getAccountNumber() != null) {
                                value = transaction.getAccountNumber();
                                if (transaction.getBankBrandName() != null) {
                                    value += " (" + transaction.getBankBrandName() + ")";
                                }
                            }
                            break;

                        case "reference":
                            value = transaction.getReferenceNumber() != null ? transaction.getReferenceNumber() : "";
                            break;
                    }

                    values.add(value);
                }

                csvPrinter.printRecord(values);
            }

            csvPrinter.flush();
            writer.flush();
        }

        return outputStream.toByteArray();
    }

    private List<Transaction> sortTransactions(List<Transaction> transactions, String sortOrder) {
        if (sortOrder == null || transactions == null || transactions.isEmpty()) {
            return transactions;
        }

        Comparator<Transaction> comparator;

        switch (sortOrder) {
            case "dateAsc":
                comparator = Comparator.comparing(Transaction::getTransactionDate, Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            case "dateDesc":
                comparator = Comparator.comparing(Transaction::getTransactionDate, Comparator.nullsLast(Comparator.reverseOrder()));
                break;
            case "amountAsc":
                comparator = (t1, t2) -> Double.compare(getTransactionAmount(t1), getTransactionAmount(t2));
                break;
            case "amountDesc":
                comparator = (t1, t2) -> Double.compare(getTransactionAmount(t2), getTransactionAmount(t1));
                break;
            default:
                return transactions;
        }

        return transactions.stream()
                .sorted(comparator)
                .collect(Collectors.toList());
    }

    private double getTransactionAmount(Transaction transaction) {
        if (transaction.getAmountIn() != null && transaction.getAmountIn() > 0) {
            return transaction.getAmountIn();
        } else if (transaction.getAmountOut() != null && transaction.getAmountOut() > 0) {
            return transaction.getAmountOut();
        }
        return 0.0;
    }
}
