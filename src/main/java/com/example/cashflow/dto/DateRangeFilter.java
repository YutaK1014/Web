package com.example.cashflow.dto;

import java.time.LocalDate;
import java.time.DateTimeException;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.validation.Errors;

@Getter
@Setter
public class DateRangeFilter {
    private String period = "";
    private String from = "";
    private String to = "";
    private final LocalDate today;

    public DateRangeFilter() { this(LocalDate.now()); }
    public DateRangeFilter(LocalDate today) { this.today = today; }

    public boolean isPreset() { return "1month".equals(period) || "3months".equals(period); }

    public void validate(Errors errors) {
        if (!List.of("", "custom", "1month", "3months").contains(period == null ? "invalid" : period)) {
            errors.rejectValue("period", "invalid", "検索期間を選び直してください。");
            return;
        }
        if (isPreset()) return;
        try {
            if ("custom".equals(period) && (from == null || from.isBlank() || to == null || to.isBlank()))
                errors.reject("required", "期間指定では開始日と終了日を入力してください。");
            LocalDate start = date(from);
            LocalDate end = date(to);
            if (start != null && end != null && start.isAfter(end))
                errors.reject("range", "開始日は終了日以前にしてください。");
        } catch (DateTimeException exception) {
            errors.reject("date", "開始日・終了日は1000年〜9999年の正しい日付で入力してください。");
        }
    }

    private LocalDate date(String value) {
        if (value == null || value.isBlank()) return null;
        LocalDate date = LocalDate.parse(value);
        if (date.getYear() < 1000 || date.getYear() > 9999) throw new DateTimeException("range");
        return date;
    }

    public LocalDate start() {
        return isPreset() ? today.minusMonths("1month".equals(period) ? 1 : 3) : date(from);
    }

    public LocalDate end() { return isPreset() ? today : date(to); }
}
