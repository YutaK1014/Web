package com.example.cashflow.dto;

import java.time.LocalDate;
import java.time.YearMonth;
import lombok.Data;
import org.springframework.validation.Errors;

@Data
public class TransactionFilter {
    private String keyword = "";
    private String marketplace = "";
    private String month = "";
    private String from = "";
    private String to = "";

    public void validate(Errors errors) {
        try {
            // Validate each original value before intersecting month and date bounds.
            if (!month.isBlank()) validateYear(YearMonth.parse(month).getYear());
            if (!from.isBlank()) validateYear(LocalDate.parse(from).getYear());
            if (!to.isBlank()) validateYear(LocalDate.parse(to).getYear());
            LocalDate start = start();
            LocalDate end = end();
            if (start != null && end != null && start.isAfter(end))
                errors.reject("range", "開始日は終了日以前にしてください。販売月と期間の重なりも確認してください。");
        } catch (java.time.DateTimeException exception) {
            errors.reject("date", "販売月・販売期間を正しく入力してください。");
        }
        if (!marketplace.isBlank() && !TransactionForm.PLATFORMS.contains(marketplace) && !"ヤフーオークション".equals(marketplace))
            errors.reject("marketplace", "フリマサイトを選び直してください。");
        if (keyword.length() > 100) errors.reject("keyword", "検索語は100文字以内で入力してください。");
    }

    private void validateYear(int year) {
        if (year < 1000 || year > 9999)
            throw new java.time.DateTimeException("販売日の年は1000〜9999です。");
    }

    public LocalDate start() {
        LocalDate date = from.isBlank() ? null : LocalDate.parse(from);
        if (!month.isBlank()) {
            LocalDate monthStart = YearMonth.parse(month).atDay(1);
            if (date == null || monthStart.isAfter(date)) date = monthStart;
        }
        return date;
    }

    public LocalDate end() {
        LocalDate date = to.isBlank() ? null : LocalDate.parse(to);
        if (!month.isBlank()) {
            LocalDate monthEnd = YearMonth.parse(month).atEndOfMonth();
            if (date == null || monthEnd.isBefore(date)) date = monthEnd;
        }
        return date;
    }
}
