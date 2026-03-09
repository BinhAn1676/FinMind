package com.finance.financeservice.service.impl;

import com.finance.financeservice.mongo.document.Transaction;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Comparator;
import java.util.stream.Collectors;

import com.finance.financeservice.service.TransactionService;

@Service
public class ExcelExportService {

    public byte[] generateExcelFile(List<Transaction> transactions, String sortOrder, Boolean includeSummarySheet, List<String> selectedColumns) throws IOException {
        // Sort transactions based on sortOrder parameter
        transactions = sortTransactions(transactions, sortOrder);

        Workbook workbook = new XSSFWorkbook();

        // Create summary sheet if requested
        if (includeSummarySheet != null && includeSummarySheet) {
            createSummarySheet(workbook, transactions);
        }

        // Create transactions sheet with selected columns
        createTransactionsSheet(workbook, transactions, selectedColumns);

        // Write to byte array
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();

        return outputStream.toByteArray();
    }

    private List<Transaction> sortTransactions(List<Transaction> transactions, String sortOrder) {
        if (sortOrder == null || transactions == null || transactions.isEmpty()) {
            return transactions;
        }

        Comparator<Transaction> comparator;

        switch (sortOrder) {
            case "dateAsc":
                comparator = Comparator.comparing(
                    t -> t.getTransactionDate() != null ? t.getTransactionDate() : java.time.LocalDateTime.MIN
                );
                break;
            case "dateDesc":
                comparator = Comparator.comparing(
                    (Transaction t) -> t.getTransactionDate() != null ? t.getTransactionDate() : java.time.LocalDateTime.MIN
                ).reversed();
                break;
            case "amountAsc":
                comparator = Comparator.comparing(t -> {
                    Double amount = t.getAmountIn() != null && t.getAmountIn() > 0
                        ? t.getAmountIn()
                        : (t.getAmountOut() != null ? t.getAmountOut() : 0.0);
                    return amount;
                });
                break;
            case "amountDesc":
                comparator = Comparator.comparing((Transaction t) -> {
                    Double amount = t.getAmountIn() != null && t.getAmountIn() > 0
                        ? t.getAmountIn()
                        : (t.getAmountOut() != null ? t.getAmountOut() : 0.0);
                    return amount;
                }).reversed();
                break;
            default:
                // Default to date descending
                comparator = Comparator.comparing(
                    (Transaction t) -> t.getTransactionDate() != null ? t.getTransactionDate() : java.time.LocalDateTime.MIN
                ).reversed();
        }

        return transactions.stream()
            .sorted(comparator)
            .collect(Collectors.toList());
    }

    private void createSummarySheet(Workbook workbook, List<Transaction> transactions) {
        Sheet summarySheet = workbook.createSheet("Summary");

        // Calculate statistics
        int transactionCount = transactions.size();
        double totalIncome = transactions.stream()
            .filter(t -> t.getAmountIn() != null && t.getAmountIn() > 0)
            .mapToDouble(Transaction::getAmountIn)
            .sum();
        double totalExpense = transactions.stream()
            .filter(t -> t.getAmountOut() != null && t.getAmountOut() > 0)
            .mapToDouble(Transaction::getAmountOut)
            .sum();
        double netBalance = totalIncome - totalExpense;
        double averageAmount = transactionCount > 0
            ? (totalIncome + totalExpense) / transactionCount
            : 0.0;

        // Create styles
        CellStyle titleStyle = workbook.createCellStyle();
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 16);
        titleStyle.setFont(titleFont);

        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 12);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle numberStyle = workbook.createCellStyle();
        CreationHelper createHelper = workbook.getCreationHelper();
        numberStyle.setDataFormat(createHelper.createDataFormat().getFormat("#,##0"));

        // Title
        Row titleRow = summarySheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("TÓM TẮT BÁO CÁO GIAO DỊCH");
        titleCell.setCellStyle(titleStyle);

        // Empty row
        summarySheet.createRow(1);

        // Statistics
        int rowNum = 2;
        createSummaryRow(summarySheet, rowNum++, "Tổng số giao dịch", transactionCount, headerStyle, numberStyle, true);
        createSummaryRow(summarySheet, rowNum++, "Tổng thu nhập", totalIncome, headerStyle, numberStyle, false);
        createSummaryRow(summarySheet, rowNum++, "Tổng chi tiêu", totalExpense, headerStyle, numberStyle, false);
        createSummaryRow(summarySheet, rowNum++, "Số dư ròng", netBalance, headerStyle, numberStyle, false);
        createSummaryRow(summarySheet, rowNum++, "Trung bình", averageAmount, headerStyle, numberStyle, false);

        // Category breakdown
        summarySheet.createRow(rowNum++);
        Row categoryTitleRow = summarySheet.createRow(rowNum++);
        Cell categoryTitleCell = categoryTitleRow.createCell(0);
        categoryTitleCell.setCellValue("PHÂN LOẠI THEO DANH MỤC");
        categoryTitleCell.setCellStyle(headerStyle);

        // Group by category
        Map<String, Double> categoryExpenses = new HashMap<>();
        Map<String, Double> categoryIncomes = new HashMap<>();

        for (Transaction t : transactions) {
            String category = t.getCategory() != null ? t.getCategory() : "Không xác định";
            if (t.getAmountOut() != null && t.getAmountOut() > 0) {
                categoryExpenses.merge(category, t.getAmountOut(), Double::sum);
            }
            if (t.getAmountIn() != null && t.getAmountIn() > 0) {
                categoryIncomes.merge(category, t.getAmountIn(), Double::sum);
            }
        }

        // Category header
        Row categoryHeaderRow = summarySheet.createRow(rowNum++);
        Cell catHeader1 = categoryHeaderRow.createCell(0);
        catHeader1.setCellValue("Danh mục");
        catHeader1.setCellStyle(headerStyle);
        Cell catHeader2 = categoryHeaderRow.createCell(1);
        catHeader2.setCellValue("Thu nhập");
        catHeader2.setCellStyle(headerStyle);
        Cell catHeader3 = categoryHeaderRow.createCell(2);
        catHeader3.setCellValue("Chi tiêu");
        catHeader3.setCellStyle(headerStyle);

        // Category data
        for (String category : categoryExpenses.keySet()) {
            Row catRow = summarySheet.createRow(rowNum++);
            catRow.createCell(0).setCellValue(category);

            Cell incomeCell = catRow.createCell(1);
            incomeCell.setCellValue(categoryIncomes.getOrDefault(category, 0.0));
            incomeCell.setCellStyle(numberStyle);

            Cell expenseCell = catRow.createCell(2);
            expenseCell.setCellValue(categoryExpenses.get(category));
            expenseCell.setCellStyle(numberStyle);
        }

        for (String category : categoryIncomes.keySet()) {
            if (!categoryExpenses.containsKey(category)) {
                Row catRow = summarySheet.createRow(rowNum++);
                catRow.createCell(0).setCellValue(category);

                Cell incomeCell = catRow.createCell(1);
                incomeCell.setCellValue(categoryIncomes.get(category));
                incomeCell.setCellStyle(numberStyle);

                catRow.createCell(2).setCellValue(0.0);
            }
        }

        // Auto-size columns
        for (int i = 0; i < 3; i++) {
            summarySheet.autoSizeColumn(i);
        }
    }

    private void createSummaryRow(Sheet sheet, int rowNum, String label, double value,
                                  CellStyle headerStyle, CellStyle numberStyle, boolean isCount) {
        Row row = sheet.createRow(rowNum);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(headerStyle);

        Cell valueCell = row.createCell(1);
        if (isCount) {
            valueCell.setCellValue((int) value);
        } else {
            valueCell.setCellValue(value);
            valueCell.setCellStyle(numberStyle);
        }
    }

    private void createTransactionsSheet(Workbook workbook, List<Transaction> transactions, List<String> selectedColumns) {
        Sheet sheet = workbook.createSheet("Transactions");

        // Column mapping: frontend value -> Vietnamese header
        Map<String, String> columnHeaders = new HashMap<>();
        columnHeaders.put("date", "Ngày giao dịch");
        columnHeaders.put("type", "Loại giao dịch");
        columnHeaders.put("amount", "Số tiền");
        columnHeaders.put("category", "Phân loại");
        columnHeaders.put("content", "Nội dung");
        columnHeaders.put("account", "Số tài khoản");
        columnHeaders.put("reference", "Số tham chiếu");

        // If no columns specified, use all columns
        List<String> columnsToShow = (selectedColumns == null || selectedColumns.isEmpty())
            ? List.of("date", "type", "amount", "category", "content", "account", "reference")
            : selectedColumns;

        // Create header row
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 12);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);

        int colIndex = 0;
        for (String column : columnsToShow) {
            Cell cell = headerRow.createCell(colIndex++);
            cell.setCellValue(columnHeaders.getOrDefault(column, column));
            cell.setCellStyle(headerStyle);
        }

        // Create styles
        CreationHelper createHelper = workbook.getCreationHelper();
        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat(createHelper.createDataFormat().getFormat("dd/mm/yyyy hh:mm:ss"));

        CellStyle numberStyle = workbook.createCellStyle();
        numberStyle.setDataFormat(createHelper.createDataFormat().getFormat("#,##0"));

        // Create data rows
        int rowNum = 1;
        for (Transaction transaction : transactions) {
            Row row = sheet.createRow(rowNum++);
            colIndex = 0;

            for (String column : columnsToShow) {
                Cell cell = row.createCell(colIndex++);

                switch (column) {
                    case "date":
                        if (transaction.getTransactionDate() != null) {
                            cell.setCellValue(java.sql.Timestamp.valueOf(transaction.getTransactionDate()));
                            cell.setCellStyle(dateStyle);
                        }
                        break;

                    case "type":
                        cell.setCellValue(transaction.getTransactionType() != null ? transaction.getTransactionType() : "");
                        break;

                    case "amount":
                        double amount = 0;
                        if (transaction.getAmountIn() != null && transaction.getAmountIn() > 0) {
                            amount = transaction.getAmountIn();
                        } else if (transaction.getAmountOut() != null && transaction.getAmountOut() > 0) {
                            amount = -transaction.getAmountOut();
                        }
                        if (amount != 0) {
                            cell.setCellValue(amount);
                            cell.setCellStyle(numberStyle);
                        }
                        break;

                    case "category":
                        cell.setCellValue(transaction.getCategory() != null ? transaction.getCategory() : "không xác định");
                        break;

                    case "content":
                        cell.setCellValue(transaction.getTransactionContent() != null ? transaction.getTransactionContent() : "");
                        break;

                    case "account":
                        String accountInfo = "";
                        if (transaction.getAccountNumber() != null) {
                            accountInfo = transaction.getAccountNumber();
                            if (transaction.getBankBrandName() != null) {
                                accountInfo += " (" + transaction.getBankBrandName() + ")";
                            }
                        }
                        cell.setCellValue(accountInfo);
                        break;

                    case "reference":
                        cell.setCellValue(transaction.getReferenceNumber() != null ? transaction.getReferenceNumber() : "");
                        break;
                }
            }
        }

        // Auto-size columns
        for (int i = 0; i < columnsToShow.size(); i++) {
            sheet.autoSizeColumn(i);
        }
    }

    public byte[] generateCashflowExcel(List<TransactionService.CashflowPoint> points, int year) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Cashflow " + year);

        // Header
        Row header = sheet.createRow(0);
        String[] headers = {"category", "Tiền vào", "Tiền ra", "Balance"};

        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setAlignment(HorizontalAlignment.LEFT);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        CreationHelper createHelper = workbook.getCreationHelper();
        CellStyle numberStyle = workbook.createCellStyle();
        numberStyle.setDataFormat(createHelper.createDataFormat().getFormat("#,##0"));

        // Map month->point for quick lookup
        Map<Integer, TransactionService.CashflowPoint> map = new HashMap<>();
        if (points != null) {
            for (TransactionService.CashflowPoint p : points) {
                map.put(p.month(), p);
            }
        }

        int rowNum = 1;
        for (int m = 1; m <= 12; m++) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue("Tháng " + m);

            TransactionService.CashflowPoint p = map.get(m);
            double income = p != null && p.totalIncome() != null ? p.totalIncome() : 0d;
            double expense = p != null && p.totalExpense() != null ? p.totalExpense() : 0d;
            double balance = p != null && p.balance() != null ? p.balance() : 0d;

            Cell c1 = row.createCell(1); c1.setCellValue(income); c1.setCellStyle(numberStyle);
            Cell c2 = row.createCell(2); c2.setCellValue(expense); c2.setCellStyle(numberStyle);
            Cell c3 = row.createCell(3); c3.setCellValue(balance); c3.setCellStyle(numberStyle);
        }

        for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        workbook.write(baos);
        workbook.close();
        return baos.toByteArray();
    }
}

