package com.finance.aiservice.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Vietnamese date expressions into date ranges.
 *
 * Supported expressions:
 * - "tháng này" / "this month" → Current month
 * - "tháng trước" / "last month" → Previous month
 * - "tháng 12" / "tháng 1" → Specific month of current year
 * - "tháng 12/2025" / "12/2025" → Specific month/year
 * - "tuần này" / "this week" → Current week
 * - "tuần trước" / "last week" → Previous week
 * - "3 tháng gần đây" / "last 3 months" → Last N months
 * - "năm nay" / "this year" → Current year
 * - "năm 2025" / "year 2025" → Specific year
 * - "hôm nay" / "today" → Today
 * - "hôm qua" / "yesterday" → Yesterday
 */
@Slf4j
@Component
public class DateRangeParser {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * Parse Vietnamese date expression into a date range.
     * @param expression Vietnamese date expression (case-insensitive)
     * @return DateRange with start and end dates (ISO format: yyyy-MM-dd)
     */
    public DateRange parse(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            // Default: last 30 days
            return getLastNDays(30);
        }

        String expr = expression.trim().toLowerCase();

        // Today / Yesterday
        if (expr.matches("(hôm nay|today)")) {
            return getToday();
        }
        if (expr.matches("(hôm qua|yesterday)")) {
            return getYesterday();
        }

        // This week / Last week
        if (expr.matches("(tuần này|this week)")) {
            return getThisWeek();
        }
        if (expr.matches("(tuần trước|last week)")) {
            return getLastWeek();
        }

        // This month / Last month
        if (expr.matches("(tháng này|this month)")) {
            return getThisMonth();
        }
        if (expr.matches("(tháng trước|last month)")) {
            return getLastMonth();
        }

        // This year / Last year
        if (expr.matches("(năm nay|this year)")) {
            return getThisYear();
        }
        if (expr.matches("(năm trước|last year)")) {
            return getLastYear();
        }

        // Specific year: "năm 2025" or "year 2025"
        Pattern yearPattern = Pattern.compile("(năm|year)\\s+(\\d{4})");
        Matcher yearMatcher = yearPattern.matcher(expr);
        if (yearMatcher.find()) {
            int year = Integer.parseInt(yearMatcher.group(2));
            return getSpecificYear(year);
        }

        // Last N months: "3 tháng gần đây" or "last 3 months"
        Pattern lastNMonthsPattern = Pattern.compile("(\\d+)\\s+(tháng gần đây|tháng qua|months?)");
        Matcher lastNMonthsMatcher = lastNMonthsPattern.matcher(expr);
        if (lastNMonthsMatcher.find()) {
            int months = Integer.parseInt(lastNMonthsMatcher.group(1));
            return getLastNMonths(months);
        }

        // Last N days: "7 ngày gần đây" or "last 7 days"
        Pattern lastNDaysPattern = Pattern.compile("(\\d+)\\s+(ngày gần đây|ngày qua|days?)");
        Matcher lastNDaysMatcher = lastNDaysPattern.matcher(expr);
        if (lastNDaysMatcher.find()) {
            int days = Integer.parseInt(lastNDaysMatcher.group(1));
            return getLastNDays(days);
        }

        // Specific month with year: "tháng 12/2025" or "12/2025"
        Pattern monthYearPattern = Pattern.compile("(tháng\\s+)?(\\d{1,2})/(\\d{4})");
        Matcher monthYearMatcher = monthYearPattern.matcher(expr);
        if (monthYearMatcher.find()) {
            int month = Integer.parseInt(monthYearMatcher.group(2));
            int year = Integer.parseInt(monthYearMatcher.group(3));
            return getSpecificMonth(year, month);
        }

        // Specific month of current year: "tháng 12" or "tháng 1"
        Pattern monthPattern = Pattern.compile("tháng\\s+(\\d{1,2})");
        Matcher monthMatcher = monthPattern.matcher(expr);
        if (monthMatcher.find()) {
            int month = Integer.parseInt(monthMatcher.group(1));
            int currentYear = LocalDate.now().getYear();
            return getSpecificMonth(currentYear, month);
        }

        log.warn("Could not parse date expression '{}', using default (last 30 days)", expression);
        return getLastNDays(30);
    }

    private DateRange getToday() {
        LocalDate today = LocalDate.now();
        return new DateRange(today.format(ISO_FORMATTER), today.format(ISO_FORMATTER));
    }

    private DateRange getYesterday() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        return new DateRange(yesterday.format(ISO_FORMATTER), yesterday.format(ISO_FORMATTER));
    }

    private DateRange getThisWeek() {
        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        return new DateRange(startOfWeek.format(ISO_FORMATTER), today.format(ISO_FORMATTER));
    }

    private DateRange getLastWeek() {
        LocalDate today = LocalDate.now();
        LocalDate startOfLastWeek = today.minusWeeks(1).with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate endOfLastWeek = startOfLastWeek.plusDays(6);
        return new DateRange(startOfLastWeek.format(ISO_FORMATTER), endOfLastWeek.format(ISO_FORMATTER));
    }

    private DateRange getThisMonth() {
        LocalDate today = LocalDate.now();
        LocalDate startOfMonth = today.with(TemporalAdjusters.firstDayOfMonth());
        return new DateRange(startOfMonth.format(ISO_FORMATTER), today.format(ISO_FORMATTER));
    }

    private DateRange getLastMonth() {
        LocalDate today = LocalDate.now();
        LocalDate firstDayOfLastMonth = today.minusMonths(1).with(TemporalAdjusters.firstDayOfMonth());
        LocalDate lastDayOfLastMonth = today.minusMonths(1).with(TemporalAdjusters.lastDayOfMonth());
        return new DateRange(firstDayOfLastMonth.format(ISO_FORMATTER), lastDayOfLastMonth.format(ISO_FORMATTER));
    }

    private DateRange getThisYear() {
        LocalDate today = LocalDate.now();
        LocalDate startOfYear = today.with(TemporalAdjusters.firstDayOfYear());
        return new DateRange(startOfYear.format(ISO_FORMATTER), today.format(ISO_FORMATTER));
    }

    private DateRange getLastYear() {
        int lastYear = LocalDate.now().getYear() - 1;
        return getSpecificYear(lastYear);
    }

    private DateRange getSpecificYear(int year) {
        LocalDate startOfYear = LocalDate.of(year, 1, 1);
        LocalDate endOfYear = LocalDate.of(year, 12, 31);
        return new DateRange(startOfYear.format(ISO_FORMATTER), endOfYear.format(ISO_FORMATTER));
    }

    private DateRange getSpecificMonth(int year, int month) {
        LocalDate firstDay = LocalDate.of(year, month, 1);
        LocalDate lastDay = firstDay.with(TemporalAdjusters.lastDayOfMonth());
        return new DateRange(firstDay.format(ISO_FORMATTER), lastDay.format(ISO_FORMATTER));
    }

    private DateRange getLastNMonths(int months) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusMonths(months);
        return new DateRange(startDate.format(ISO_FORMATTER), today.format(ISO_FORMATTER));
    }

    private DateRange getLastNDays(int days) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(days);
        return new DateRange(startDate.format(ISO_FORMATTER), today.format(ISO_FORMATTER));
    }

    /**
     * Date range with start and end dates in ISO format (yyyy-MM-dd).
     */
    public record DateRange(String startDate, String endDate) {
        public LocalDateTime startDateTime() {
            return LocalDate.parse(startDate, ISO_FORMATTER).atStartOfDay();
        }

        public LocalDateTime endDateTime() {
            return LocalDate.parse(endDate, ISO_FORMATTER).atTime(23, 59, 59);
        }
    }
}
