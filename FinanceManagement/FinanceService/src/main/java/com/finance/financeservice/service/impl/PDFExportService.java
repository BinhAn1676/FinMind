package com.finance.financeservice.service.impl;

import com.finance.financeservice.mongo.document.Transaction;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PDFExportService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final NumberFormat CURRENCY_FORMATTER = new DecimalFormat("#,###");

    public byte[] generatePdfFile(List<Transaction> transactions, String sortOrder, Boolean includeSummarySheet, List<String> selectedColumns) throws IOException {
        // Sort transactions
        transactions = sortTransactions(transactions, sortOrder);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(outputStream);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc);

        // Add title
        Paragraph title = new Paragraph("TRANSACTION REPORT")
                .setFontSize(20)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(20);
        document.add(title);

        // Add summary if requested
        if (includeSummarySheet != null && includeSummarySheet) {
            addSummarySection(document, transactions);
        }

        // Add transactions table
        addTransactionsTable(document, transactions, selectedColumns);

        document.close();
        return outputStream.toByteArray();
    }

    private void addSummarySection(Document document, List<Transaction> transactions) {
        Paragraph summaryTitle = new Paragraph("SUMMARY")
                .setFontSize(16)
                .setBold()
                .setMarginTop(10)
                .setMarginBottom(10);
        document.add(summaryTitle);

        // Calculate summary statistics
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

        // Create summary table
        float[] columnWidths = {200f, 200f};
        Table summaryTable = new Table(UnitValue.createPointArray(columnWidths));
        summaryTable.setWidth(UnitValue.createPercentValue(100));

        // Add summary rows
        addSummaryRow(summaryTable, "Total Transactions:", String.valueOf(transactionCount));
        addSummaryRow(summaryTable, "Total Income:", CURRENCY_FORMATTER.format(totalIncome) + " VND");
        addSummaryRow(summaryTable, "Total Expense:", CURRENCY_FORMATTER.format(totalExpense) + " VND");
        addSummaryRow(summaryTable, "Net Balance:", CURRENCY_FORMATTER.format(netBalance) + " VND");

        document.add(summaryTable);
        document.add(new Paragraph("\n"));
    }

    private void addSummaryRow(Table table, String label, String value) {
        Cell labelCell = new Cell().add(new Paragraph(label)).setBold().setBackgroundColor(new DeviceRgb(240, 240, 240));
        Cell valueCell = new Cell().add(new Paragraph(value));
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private void addTransactionsTable(Document document, List<Transaction> transactions, List<String> selectedColumns) {
        Paragraph transactionsTitle = new Paragraph("TRANSACTIONS")
                .setFontSize(16)
                .setBold()
                .setMarginTop(10)
                .setMarginBottom(10);
        document.add(transactionsTitle);

        // Column mapping
        Map<String, String> columnHeaders = new HashMap<>();
        columnHeaders.put("date", "Date");
        columnHeaders.put("type", "Type");
        columnHeaders.put("amount", "Amount");
        columnHeaders.put("category", "Category");
        columnHeaders.put("content", "Description");
        columnHeaders.put("account", "Account");
        columnHeaders.put("reference", "Reference");

        // Default columns if none specified
        List<String> columnsToShow = (selectedColumns == null || selectedColumns.isEmpty())
                ? List.of("date", "type", "amount", "category", "content", "account")
                : selectedColumns;

        // Create table with dynamic columns
        float[] columnWidths = new float[columnsToShow.size()];
        for (int i = 0; i < columnsToShow.size(); i++) {
            String col = columnsToShow.get(i);
            // Set different widths based on column type
            if (col.equals("date")) columnWidths[i] = 100f;
            else if (col.equals("amount")) columnWidths[i] = 80f;
            else if (col.equals("type")) columnWidths[i] = 50f;
            else if (col.equals("content")) columnWidths[i] = 150f;
            else columnWidths[i] = 80f;
        }

        Table table = new Table(UnitValue.createPointArray(columnWidths));
        table.setWidth(UnitValue.createPercentValue(100));

        // Add header row
        for (String column : columnsToShow) {
            Cell headerCell = new Cell()
                    .add(new Paragraph(columnHeaders.getOrDefault(column, column)))
                    .setBold()
                    .setBackgroundColor(new DeviceRgb(79, 195, 247))
                    .setFontColor(ColorConstants.WHITE)
                    .setTextAlignment(TextAlignment.CENTER);
            table.addHeaderCell(headerCell);
        }

        // Add data rows
        for (Transaction transaction : transactions) {
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
                        value = amount != 0 ? CURRENCY_FORMATTER.format(amount) : "";
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

                table.addCell(new Cell().add(new Paragraph(value)).setFontSize(9));
            }
        }

        document.add(table);
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
