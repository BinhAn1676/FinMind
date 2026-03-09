package com.finance.aiservice.service;

import com.finance.aiservice.dto.AnalyticsDashboardResponse.*;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Service for generating Excel and PDF financial reports.
 *
 * Vietnamese font note: PDF will render Vietnamese characters correctly if DejaVu fonts
 * are installed on the server. For Docker: RUN apt-get install -y fonts-dejavu
 */
@Slf4j
@Service
public class ReportExportService {

    // PDF colors (java.awt.Color for OpenPDF)
    private static final Color NAVY        = new Color(26, 39, 68);
    private static final Color TEAL        = new Color(78, 205, 196);
    private static final Color LIGHT_GRAY  = new Color(248, 250, 252);
    private static final Color WHITE       = Color.WHITE;
    private static final Color DARK_TEXT   = new Color(30, 30, 50);
    private static final Color GRAY_TEXT   = new Color(120, 130, 150);

    // Vietnamese font paths (tried in order)
    private static final String[] FONT_PATHS = {
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Regular.ttf",
        "/usr/share/fonts/truetype/freefont/FreeSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
        "/usr/share/fonts/dejavu/DejaVuSans.ttf"
    };

    // =========================================================================
    // Utilities
    // =========================================================================

    private static String fmtVnd(Double amount) {
        if (amount == null) return "N/A";
        NumberFormat nf = NumberFormat.getInstance(new Locale("vi", "VN"));
        nf.setMaximumFractionDigits(0);
        return nf.format(amount.longValue()) + " ₫";
    }

    private static String fmtPct(Double pct) {
        if (pct == null) return "N/A";
        return String.format("%.1f%%", pct);
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }

    // =========================================================================
    // EXCEL GENERATION
    // =========================================================================

    public byte[] generateExcel(CompleteDashboard d, int month, int year) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            ExcelStyles s = new ExcelStyles(wb);
            String period = String.format("Tháng %02d/%d", month, year);

            buildOverviewSheet(wb, s, d, period);
            buildSpendingSheet(wb, s, d.getSpendingStructure(), period);
            buildAnomaliesSheet(wb, s, d.getAnomalies(), period);
            buildForecastSheet(wb, s, d.getBudgetForecast());
            buildRecommendationsSheet(wb, s, d.getRecommendations());

            wb.write(out);
            return out.toByteArray();
        }
    }

    // ---- Sheet 1: Tổng quan ----
    private void buildOverviewSheet(XSSFWorkbook wb, ExcelStyles s, CompleteDashboard d, String period) {
        XSSFSheet sheet = wb.createSheet("Tổng quan");
        int r = 0;

        r = addExcelTitle(sheet, s, r, "BÁO CÁO TÀI CHÍNH - " + period.toUpperCase(), 3);
        r++; // blank

        r = addExcelSectionHeader(sheet, s, r, "ĐIỂM SỨC KHỎE TÀI CHÍNH", 3);
        HealthScore hs = d.getHealthScore();
        if (hs != null) {
            addLV(sheet, s, r++, "Điểm số:", hs.getScore() != null ? hs.getScore() + "/100" : "N/A");
            addLV(sheet, s, r++, "Xếp loại:", safe(hs.getGrade()));
            addLV(sheet, s, r++, "Trạng thái:", safe(hs.getStatus()));
            addLV(sheet, s, r++, "Nhận xét:", safe(hs.getMessage()));
            if (hs.getAiNarrative() != null) {
                addLV(sheet, s, r++, "Đánh giá AI:", hs.getAiNarrative());
            }
            r++; // blank
            if (hs.getStrengths() != null && !hs.getStrengths().isEmpty()) {
                addLV(sheet, s, r++, "Điểm mạnh:", String.join("; ", hs.getStrengths()));
            }
            if (hs.getConcerns() != null && !hs.getConcerns().isEmpty()) {
                addLV(sheet, s, r++, "Cần cải thiện:", String.join("; ", hs.getConcerns()));
            }
        }

        sheet.setColumnWidth(0, 6500);
        sheet.setColumnWidth(1, 20000);
        sheet.setColumnWidth(2, 4000);
    }

    // ---- Sheet 2: Cơ cấu chi tiêu ----
    private void buildSpendingSheet(XSSFWorkbook wb, ExcelStyles s, SpendingStructure ss, String period) {
        XSSFSheet sheet = wb.createSheet("Cơ cấu chi tiêu");
        int r = 0;

        r = addExcelTitle(sheet, s, r, "CƠ CẤU CHI TIÊU - " + period.toUpperCase(), 5);
        r++; // blank

        if (ss != null) {
            addLV(sheet, s, r++, "Tổng thu nhập:", fmtVnd(ss.getTotalIncome()));
            addLV(sheet, s, r++, "Tổng chi tiêu:", fmtVnd(ss.getTotalExpense()));
            addLV(sheet, s, r++, "Trạng thái:", safe(ss.getOverallStatus()));
            addLV(sheet, s, r++, "Khuyến nghị:", safe(ss.getRecommendation()));
            r++; // blank

            // Table header
            Row hRow = sheet.createRow(r++);
            hRow.setHeightInPoints(22);
            String[] headers = {"Danh mục", "% Thực tế", "% Lý tưởng", "Số tiền", "Trạng thái"};
            for (int i = 0; i < headers.length; i++) {
                Cell c = hRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(s.colHeader);
            }

            if (ss.getCategories() != null) {
                boolean alt = false;
                for (SpendingCategory cat : ss.getCategories()) {
                    Row row = sheet.createRow(r++);
                    CellStyle rs = alt ? s.altRow : s.dataRow;
                    setCell(row, 0, safe(cat.getName()), rs);
                    setCell(row, 1, fmtPct(cat.getActualPercent()), rs);
                    setCell(row, 2, fmtPct(cat.getIdealPercent()), rs);
                    setCell(row, 3, fmtVnd(cat.getAmount()), rs);
                    Cell sc = row.createCell(4);
                    sc.setCellValue(safe(cat.getStatus()));
                    sc.setCellStyle(statusStyle(s, cat.getStatus()));
                    alt = !alt;
                }
            }
        }

        sheet.setColumnWidth(0, 8000);
        sheet.setColumnWidth(1, 4000);
        sheet.setColumnWidth(2, 4000);
        sheet.setColumnWidth(3, 7000);
        sheet.setColumnWidth(4, 5500);
    }

    // ---- Sheet 3: Giao dịch bất thường ----
    private void buildAnomaliesSheet(XSSFWorkbook wb, ExcelStyles s, List<AnomalyAlert> anomalies, String period) {
        XSSFSheet sheet = wb.createSheet("Giao dịch bất thường");
        int r = 0;

        r = addExcelTitle(sheet, s, r, "GIAO DỊCH BẤT THƯỜNG - " + period.toUpperCase(), 6);
        r++; // blank

        Row hRow = sheet.createRow(r++);
        hRow.setHeightInPoints(22);
        String[] headers = {"Mô tả", "Số tiền", "Ngày", "Mức độ", "Lý do", "Khuyến nghị"};
        for (int i = 0; i < headers.length; i++) {
            Cell c = hRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(s.colHeader);
        }

        if (anomalies != null) {
            boolean alt = false;
            for (AnomalyAlert a : anomalies) {
                Row row = sheet.createRow(r++);
                CellStyle rs = alt ? s.altRow : s.dataRow;
                setCell(row, 0, safe(a.getDescription()), rs);
                setCell(row, 1, fmtVnd(a.getAmount()), rs);
                setCell(row, 2, safe(a.getDate()), rs);
                Cell sevCell = row.createCell(3);
                sevCell.setCellValue(safe(a.getSeverity()));
                sevCell.setCellStyle(severityStyle(s, a.getSeverity()));
                setCell(row, 4, safe(a.getReason()), rs);
                setCell(row, 5, safe(a.getRecommendation()), rs);
                alt = !alt;
            }
        }

        sheet.setColumnWidth(0, 9000);
        sheet.setColumnWidth(1, 5500);
        sheet.setColumnWidth(2, 4000);
        sheet.setColumnWidth(3, 4500);
        sheet.setColumnWidth(4, 9000);
        sheet.setColumnWidth(5, 11000);
    }

    // ---- Sheet 4: Dự báo ngân sách ----
    private void buildForecastSheet(XSSFWorkbook wb, ExcelStyles s, BudgetForecast f) {
        XSSFSheet sheet = wb.createSheet("Dự báo ngân sách");
        int r = 0;

        r = addExcelTitle(sheet, s, r, "DỰ BÁO NGÂN SÁCH", 4);
        r++; // blank

        if (f != null) {
            addLV(sheet, s, r++, "Kỳ dự báo:", safe(f.getForecastPeriod()));
            addLV(sheet, s, r++, "Số dư dự kiến:", fmtVnd(f.getProjectedBalance()));
            addLV(sheet, s, r++, "Chi tiêu dự kiến:", fmtVnd(f.getProjectedExpense()));
            addLV(sheet, s, r++, "Xu hướng:", safe(f.getTrend()));
            addLV(sheet, s, r++, "Độ tin cậy:", fmtPct(f.getConfidence()));

            if (f.getWarning() != null) {
                r++; // blank
                r = addExcelSectionHeader(sheet, s, r, "⚠ CẢNH BÁO", 4);
                addLV(sheet, s, r++, "Mức độ:", safe(f.getWarning().getSeverity()));
                addLV(sheet, s, r++, "Thông báo:", safe(f.getWarning().getMessage()));
                addLV(sheet, s, r++, "Khuyến nghị:", safe(f.getWarning().getRecommendation()));
            }

            if (f.getTimeline() != null && !f.getTimeline().isEmpty()) {
                r++; // blank
                r = addExcelSectionHeader(sheet, s, r, "LỊCH DỰ BÁO", 4);
                Row hRow = sheet.createRow(r++);
                hRow.setHeightInPoints(22);
                String[] hdrs = {"Tuần", "Số dư", "Chi tiêu", "Thu nhập"};
                for (int i = 0; i < hdrs.length; i++) {
                    Cell c = hRow.createCell(i);
                    c.setCellValue(hdrs[i]);
                    c.setCellStyle(s.colHeader);
                }
                boolean alt = false;
                for (ForecastPoint fp : f.getTimeline()) {
                    Row row = sheet.createRow(r++);
                    CellStyle rs = alt ? s.altRow : s.dataRow;
                    setCell(row, 0, safe(fp.getPeriod()), rs);
                    setCell(row, 1, fmtVnd(fp.getBalance()), rs);
                    setCell(row, 2, fmtVnd(fp.getExpense()), rs);
                    setCell(row, 3, fmtVnd(fp.getIncome()), rs);
                    alt = !alt;
                }
            }
        }

        sheet.setColumnWidth(0, 8000);
        sheet.setColumnWidth(1, 8000);
        sheet.setColumnWidth(2, 8000);
        sheet.setColumnWidth(3, 8000);
    }

    // ---- Sheet 5: Khuyến nghị ----
    private void buildRecommendationsSheet(XSSFWorkbook wb, ExcelStyles s, List<DisciplineRecommendation> recs) {
        XSSFSheet sheet = wb.createSheet("Khuyến nghị");
        int r = 0;

        r = addExcelTitle(sheet, s, r, "KHUYẾN NGHỊ TÀI CHÍNH", 5);
        r++; // blank

        Row hRow = sheet.createRow(r++);
        hRow.setHeightInPoints(22);
        String[] headers = {"Ưu tiên", "Danh mục", "Hiện tại", "Mục tiêu", "Tiết kiệm tiềm năng"};
        for (int i = 0; i < headers.length; i++) {
            Cell c = hRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(s.colHeader);
        }

        if (recs != null) {
            boolean alt = false;
            for (DisciplineRecommendation rec : recs) {
                Row row = sheet.createRow(r++);
                CellStyle rs = alt ? s.altRow : s.dataRow;
                setCell(row, 0, rec.getPriority() != null ? "#" + rec.getPriority() : "", rs);
                setCell(row, 1, safe(rec.getCategory()), rs);
                setCell(row, 2, fmtVnd(rec.getCurrentAmount()), rs);
                setCell(row, 3, fmtVnd(rec.getTargetAmount()), rs);
                setCell(row, 4, fmtVnd(rec.getSavingsPotential()), rs);
                alt = !alt;
            }
        }

        sheet.setColumnWidth(0, 4000);
        sheet.setColumnWidth(1, 8000);
        sheet.setColumnWidth(2, 7000);
        sheet.setColumnWidth(3, 7000);
        sheet.setColumnWidth(4, 8000);
    }

    // ---- Excel helpers ----

    private int addExcelTitle(XSSFSheet sheet, ExcelStyles s, int r, String text, int mergeSpan) {
        Row row = sheet.createRow(r);
        row.setHeightInPoints(32);
        Cell cell = row.createCell(0);
        cell.setCellValue(text);
        cell.setCellStyle(s.title);
        sheet.addMergedRegion(new CellRangeAddress(r, r, 0, mergeSpan - 1));
        return r + 1;
    }

    private int addExcelSectionHeader(XSSFSheet sheet, ExcelStyles s, int r, String text, int mergeSpan) {
        Row row = sheet.createRow(r);
        row.setHeightInPoints(22);
        Cell cell = row.createCell(0);
        cell.setCellValue(text);
        cell.setCellStyle(s.sectionHeader);
        sheet.addMergedRegion(new CellRangeAddress(r, r, 0, mergeSpan - 1));
        return r + 1;
    }

    private void addLV(XSSFSheet sheet, ExcelStyles s, int r, String label, String value) {
        Row row = sheet.createRow(r);
        row.setHeightInPoints(20);
        Cell lc = row.createCell(0);
        lc.setCellValue(label);
        lc.setCellStyle(s.label);
        Cell vc = row.createCell(1);
        vc.setCellValue(value);
        vc.setCellStyle(s.value);
    }

    private void setCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private CellStyle statusStyle(ExcelStyles s, String status) {
        if (status == null) return s.dataRow;
        switch (status.toUpperCase()) {
            case "GOOD":            return s.goodStatus;
            case "WARNING":
            case "NEEDS_ATTENTION": return s.warnStatus;
            case "CRITICAL":
            case "ALERT":           return s.dangerStatus;
            default:                return s.dataRow;
        }
    }

    private CellStyle severityStyle(ExcelStyles s, String severity) {
        if (severity == null) return s.dataRow;
        switch (severity.toUpperCase()) {
            case "LOW":     return s.goodStatus;
            case "MEDIUM":  return s.warnStatus;
            case "HIGH":
            case "CRITICAL":return s.dangerStatus;
            default:        return s.dataRow;
        }
    }

    // ---- Excel style holder ----
    private static class ExcelStyles {
        final CellStyle title, sectionHeader, colHeader, label, value, dataRow, altRow;
        final CellStyle goodStatus, warnStatus, dangerStatus;

        ExcelStyles(XSSFWorkbook wb) {
            title        = mkTitle(wb);
            sectionHeader= mkSectionHeader(wb);
            colHeader    = mkColHeader(wb);
            label        = mkLabel(wb);
            value        = mkValue(wb);
            dataRow      = mkDataRow(wb, false);
            altRow       = mkDataRow(wb, true);
            goodStatus   = mkStatusCell(wb, new byte[]{74, (byte)222, (byte)128});   // #4ade80
            warnStatus   = mkStatusCell(wb, new byte[]{(byte)249, 115, 22});  // #f97316
            dangerStatus = mkStatusCell(wb, new byte[]{(byte)255, 107, 107}); // #FF6B6B
        }

        private static XSSFFont whiteBold(XSSFWorkbook wb, short size) {
            XSSFFont f = wb.createFont();
            f.setBold(true);
            f.setFontHeightInPoints(size);
            f.setColor(new XSSFColor(new byte[]{(byte)255, (byte)255, (byte)255}, null));
            return f;
        }

        private static XSSFFont darkFont(XSSFWorkbook wb, boolean bold) {
            XSSFFont f = wb.createFont();
            f.setBold(bold);
            f.setFontHeightInPoints((short)10);
            f.setColor(new XSSFColor(new byte[]{30, 30, 50}, null));
            return f;
        }

        private static CellStyle mkTitle(XSSFWorkbook wb) {
            XSSFCellStyle s = wb.createCellStyle();
            s.setFillForegroundColor(new XSSFColor(new byte[]{26, 39, (byte)68}, null));
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            s.setAlignment(HorizontalAlignment.CENTER);
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            s.setFont(whiteBold(wb, (short)14));
            return s;
        }

        private static CellStyle mkSectionHeader(XSSFWorkbook wb) {
            XSSFCellStyle s = wb.createCellStyle();
            s.setFillForegroundColor(new XSSFColor(new byte[]{78, (byte)205, (byte)196}, null));
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            s.setAlignment(HorizontalAlignment.LEFT);
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            s.setFont(whiteBold(wb, (short)11));
            return s;
        }

        private static CellStyle mkColHeader(XSSFWorkbook wb) {
            XSSFCellStyle s = wb.createCellStyle();
            s.setFillForegroundColor(new XSSFColor(new byte[]{26, 39, (byte)68}, null));
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            s.setAlignment(HorizontalAlignment.CENTER);
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            s.setFont(whiteBold(wb, (short)10));
            s.setBorderBottom(BorderStyle.THIN);
            return s;
        }

        private static CellStyle mkLabel(XSSFWorkbook wb) {
            XSSFCellStyle s = wb.createCellStyle();
            s.setFont(darkFont(wb, true));
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            return s;
        }

        private static CellStyle mkValue(XSSFWorkbook wb) {
            XSSFCellStyle s = wb.createCellStyle();
            s.setFont(darkFont(wb, false));
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            s.setWrapText(true);
            return s;
        }

        private static CellStyle mkDataRow(XSSFWorkbook wb, boolean alt) {
            XSSFCellStyle s = wb.createCellStyle();
            if (alt) {
                s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)248, (byte)250, (byte)252}, null));
                s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            s.setFont(darkFont(wb, false));
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            s.setBorderBottom(BorderStyle.HAIR);
            return s;
        }

        private static CellStyle mkStatusCell(XSSFWorkbook wb, byte[] rgb) {
            XSSFCellStyle s = wb.createCellStyle();
            s.setFillForegroundColor(new XSSFColor(rgb, null));
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            s.setAlignment(HorizontalAlignment.CENTER);
            s.setFont(darkFont(wb, true));
            return s;
        }
    }

    // =========================================================================
    // PDF GENERATION
    // =========================================================================

    public byte[] generatePdf(CompleteDashboard d, int month, int year) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 54, 40);
        PdfWriter.getInstance(doc, out);
        doc.open();

        try {
            BaseFont vietBf = loadVietnameseFont();

            Font titleFont   = makeFont(vietBf, 18, Font.BOLD,   NAVY);
            Font sectionFont = makeFont(vietBf, 13, Font.BOLD,   NAVY);
            Font boldFont    = makeFont(vietBf, 10, Font.BOLD,   DARK_TEXT);
            Font bodyFont    = makeFont(vietBf, 10, Font.NORMAL, DARK_TEXT);
            Font smallFont   = makeFont(vietBf,  9, Font.NORMAL, GRAY_TEXT);
            Font headerFont  = makeFont(vietBf, 10, Font.BOLD,   WHITE);

            String period = String.format("Tháng %02d/%d", month, year);

            // ---- Document title ----
            Paragraph title = new Paragraph("BÁO CÁO TÀI CHÍNH", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(4f);
            doc.add(title);

            Paragraph sub = new Paragraph(period.toUpperCase() + "  |  Tạo lúc: " + safe(d.getGeneratedAt()), smallFont);
            sub.setAlignment(Element.ALIGN_CENTER);
            sub.setSpacingAfter(16f);
            doc.add(sub);

            addPdfLine(doc);

            // ---- 1. Health Score ----
            addPdfSection(doc, "1. Điểm Sức Khỏe Tài Chính", sectionFont);
            HealthScore hs = d.getHealthScore();
            if (hs != null) {
                addKV(doc, "Điểm số: ", hs.getScore() != null ? hs.getScore() + "/100" : "N/A", boldFont, bodyFont);
                addKV(doc, "Xếp loại: ", safe(hs.getGrade()), boldFont, bodyFont);
                addKV(doc, "Trạng thái: ", safe(hs.getStatus()), boldFont, bodyFont);
                addKV(doc, "Nhận xét: ", safe(hs.getMessage()), boldFont, bodyFont);
                if (hs.getAiNarrative() != null) {
                    addKV(doc, "Đánh giá AI: ", hs.getAiNarrative(), boldFont, bodyFont);
                }
                if (hs.getStrengths() != null && !hs.getStrengths().isEmpty()) {
                    addKV(doc, "Điểm mạnh: ", String.join("; ", hs.getStrengths()), boldFont, bodyFont);
                }
                if (hs.getConcerns() != null && !hs.getConcerns().isEmpty()) {
                    addKV(doc, "Cần cải thiện: ", String.join("; ", hs.getConcerns()), boldFont, bodyFont);
                }
            }

            // ---- 2. Spending Structure ----
            addPdfSection(doc, "2. Cơ Cấu Chi Tiêu", sectionFont);
            SpendingStructure ss = d.getSpendingStructure();
            if (ss != null) {
                addKV(doc, "Tổng thu nhập: ", fmtVnd(ss.getTotalIncome()), boldFont, bodyFont);
                addKV(doc, "Tổng chi tiêu: ", fmtVnd(ss.getTotalExpense()), boldFont, bodyFont);
                addKV(doc, "Khuyến nghị: ", safe(ss.getRecommendation()), boldFont, bodyFont);

                if (ss.getCategories() != null && !ss.getCategories().isEmpty()) {
                    doc.add(new Paragraph(" "));
                    PdfPTable table = new PdfPTable(new float[]{3f, 1.5f, 1.5f, 2.5f, 1.5f});
                    table.setWidthPercentage(100);
                    table.setSpacingBefore(5f);
                    addPdfTableHeader(table, headerFont, "Danh mục", "% Thực tế", "% Lý tưởng", "Số tiền", "Trạng thái");
                    boolean alt = false;
                    for (SpendingCategory cat : ss.getCategories()) {
                        Color bg = alt ? LIGHT_GRAY : WHITE;
                        addPdfRow(table, bodyFont, bg,
                            safe(cat.getName()), fmtPct(cat.getActualPercent()),
                            fmtPct(cat.getIdealPercent()), fmtVnd(cat.getAmount()), safe(cat.getStatus()));
                        alt = !alt;
                    }
                    doc.add(table);
                }
            }

            // ---- 3. Anomalies ----
            List<AnomalyAlert> anomalies = d.getAnomalies();
            if (anomalies != null && !anomalies.isEmpty()) {
                doc.newPage();
                addPdfSection(doc, "3. Giao Dịch Bất Thường", sectionFont);
                PdfPTable table = new PdfPTable(new float[]{2.5f, 2f, 1.5f, 1.5f, 2.5f});
                table.setWidthPercentage(100);
                table.setSpacingBefore(5f);
                addPdfTableHeader(table, headerFont, "Mô tả", "Số tiền", "Ngày", "Mức độ", "Lý do");
                boolean alt = false;
                for (AnomalyAlert a : anomalies) {
                    Color bg = alt ? LIGHT_GRAY : WHITE;
                    addPdfRow(table, bodyFont, bg,
                        safe(a.getDescription()), fmtVnd(a.getAmount()),
                        safe(a.getDate()), safe(a.getSeverity()), safe(a.getReason()));
                    alt = !alt;
                }
                doc.add(table);
            }

            // ---- 4. Budget Forecast ----
            BudgetForecast f = d.getBudgetForecast();
            if (f != null) {
                addPdfSection(doc, "4. Dự Báo Ngân Sách", sectionFont);
                addKV(doc, "Kỳ dự báo: ", safe(f.getForecastPeriod()), boldFont, bodyFont);
                addKV(doc, "Số dư dự kiến: ", fmtVnd(f.getProjectedBalance()), boldFont, bodyFont);
                addKV(doc, "Chi tiêu dự kiến: ", fmtVnd(f.getProjectedExpense()), boldFont, bodyFont);
                addKV(doc, "Xu hướng: ", safe(f.getTrend()), boldFont, bodyFont);
                addKV(doc, "Độ tin cậy: ", fmtPct(f.getConfidence()), boldFont, bodyFont);

                if (f.getWarning() != null) {
                    Paragraph warn = new Paragraph("⚠  " + safe(f.getWarning().getMessage()), boldFont);
                    warn.setSpacingBefore(8f);
                    warn.setSpacingAfter(4f);
                    doc.add(warn);
                    if (f.getWarning().getRecommendation() != null) {
                        doc.add(new Paragraph("→ " + f.getWarning().getRecommendation(), bodyFont));
                    }
                }

                if (f.getTimeline() != null && !f.getTimeline().isEmpty()) {
                    doc.add(new Paragraph(" "));
                    PdfPTable table = new PdfPTable(new float[]{1.5f, 2.5f, 2.5f, 2.5f});
                    table.setWidthPercentage(100);
                    table.setSpacingBefore(5f);
                    addPdfTableHeader(table, headerFont, "Tuần", "Số dư", "Chi tiêu", "Thu nhập");
                    boolean alt = false;
                    for (ForecastPoint fp : f.getTimeline()) {
                        Color bg = alt ? LIGHT_GRAY : WHITE;
                        addPdfRow(table, bodyFont, bg,
                            safe(fp.getPeriod()), fmtVnd(fp.getBalance()),
                            fmtVnd(fp.getExpense()), fmtVnd(fp.getIncome()));
                        alt = !alt;
                    }
                    doc.add(table);
                }
            }

            // ---- 5. Recommendations ----
            List<DisciplineRecommendation> recs = d.getRecommendations();
            if (recs != null && !recs.isEmpty()) {
                addPdfSection(doc, "5. Khuyến Nghị Tài Chính", sectionFont);
                PdfPTable table = new PdfPTable(new float[]{1f, 2.5f, 2f, 2f, 2.5f});
                table.setWidthPercentage(100);
                table.setSpacingBefore(5f);
                addPdfTableHeader(table, headerFont, "Ưu tiên", "Danh mục", "Hiện tại", "Mục tiêu", "Tiết kiệm");
                boolean alt = false;
                for (DisciplineRecommendation rec : recs) {
                    Color bg = alt ? LIGHT_GRAY : WHITE;
                    addPdfRow(table, bodyFont, bg,
                        rec.getPriority() != null ? "#" + rec.getPriority() : "",
                        safe(rec.getCategory()),
                        fmtVnd(rec.getCurrentAmount()),
                        fmtVnd(rec.getTargetAmount()),
                        fmtVnd(rec.getSavingsPotential()));
                    alt = !alt;
                }
                doc.add(table);
            }

        } finally {
            doc.close();
        }

        return out.toByteArray();
    }

    // ---- PDF helpers ----

    private Font makeFont(BaseFont bf, int size, int style, Color color) {
        if (bf != null) return new Font(bf, size, style, color);
        // Fallback to built-in Helvetica (Vietnamese diacritics may not render)
        return new Font(Font.HELVETICA, size, style, color);
    }

    private void addPdfSection(Document doc, String text, Font font) throws DocumentException {
        doc.add(new Paragraph(" "));
        Paragraph p = new Paragraph(text, font);
        p.setSpacingBefore(10f);
        p.setSpacingAfter(6f);
        doc.add(p);
        addPdfLine(doc);
        doc.add(new Paragraph(" "));
    }

    private void addPdfLine(Document doc) throws DocumentException {
        LineSeparator line = new LineSeparator();
        line.setLineColor(TEAL);
        line.setLineWidth(1f);
        doc.add(new Chunk(line));
    }

    private void addKV(Document doc, String key, String val, Font boldFont, Font bodyFont) throws DocumentException {
        Phrase phrase = new Phrase();
        phrase.add(new Chunk(key, boldFont));
        phrase.add(new Chunk(val != null ? val : "N/A", bodyFont));
        Paragraph p = new Paragraph(phrase);
        p.setSpacingAfter(4f);
        doc.add(p);
    }

    private void addPdfTableHeader(PdfPTable table, Font font, String... headers) {
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, font));
            cell.setBackgroundColor(NAVY);
            cell.setPadding(6f);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            table.addCell(cell);
        }
    }

    private void addPdfRow(PdfPTable table, Font font, Color bg, String... values) {
        for (String v : values) {
            PdfPCell cell = new PdfPCell(new Phrase(v != null ? v : "", font));
            cell.setBackgroundColor(bg);
            cell.setPadding(5f);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            table.addCell(cell);
        }
    }

    private BaseFont loadVietnameseFont() {
        for (String path : FONT_PATHS) {
            try {
                if (new File(path).exists()) {
                    BaseFont bf = BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                    log.debug("Loaded Vietnamese font: {}", path);
                    return bf;
                }
            } catch (Exception e) {
                log.debug("Cannot load font from {}: {}", path, e.getMessage());
            }
        }
        log.warn("No Vietnamese font found. PDF may not render Vietnamese correctly. " +
                 "Fix: add 'RUN apt-get install -y fonts-dejavu' to your Dockerfile.");
        return null;
    }
}
