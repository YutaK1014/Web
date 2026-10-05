package com.example.cashflow.service;

import com.example.cashflow.entity.Transaction;
import com.example.cashflow.repository.TransactionRepository;
import java.time.YearMonth;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ReportService {
    public record Summary(long sales, long profit, long shipping, long fees, long purchases, long count,
                          long returnCosts, long returnCount) {}
    public record Group(String label, Summary summary) {}
    public record Bar(String label, long profit, double width) {}
    public record Comparison(String label, String unit, long current, long previous) {
        public long difference() { return current - previous; }
    }
    public record CalendarDay(LocalDate date, long profit, long count, long returnCount) {}

    public Summary monthSummary(List<Transaction> rows, YearMonth month) {
        return summarize(rows.stream().filter(t -> YearMonth.from(t.getSoldDate()).equals(month)).toList());
    }

    private long average(Summary summary) {
        return summary.count() == 0 ? 0 : BigDecimal.valueOf(summary.profit())
            .divide(BigDecimal.valueOf(summary.count()), 0, RoundingMode.HALF_UP).longValueExact();
    }

    public List<Comparison> compare(List<Transaction> rows, YearMonth month) {
        var current = monthSummary(rows, month);
        var previous = monthSummary(rows, month.minusMonths(1));
        return List.of(new Comparison("利益", "円", current.profit(), previous.profit()),
            new Comparison("販売件数", "件", current.count(), previous.count()),
            new Comparison("平均利益", "円", average(current), average(previous)));
    }

    public List<List<CalendarDay>> calendar(List<Transaction> rows, YearMonth month) {
        Map<LocalDate, List<Transaction>> daily = new HashMap<>();
        for (Transaction row : rows) {
            if (YearMonth.from(row.getSoldDate()).equals(month))
                daily.computeIfAbsent(row.getSoldDate(), key -> new ArrayList<>()).add(row);
        }
        List<CalendarDay> cells = new ArrayList<>();
        int offset = month.atDay(1).getDayOfWeek().getValue() % 7;
        for (int i = 0; i < offset; i++) cells.add(new CalendarDay(null, 0, 0, 0));
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate date = month.atDay(day);
            var summary = summarize(daily.getOrDefault(date, List.of()));
            cells.add(new CalendarDay(date, summary.profit(), summary.count(), summary.returnCount()));
        }
        while (cells.size() % 7 != 0) cells.add(new CalendarDay(null, 0, 0, 0));
        List<List<CalendarDay>> weeks = new ArrayList<>();
        for (int i = 0; i < cells.size(); i += 7) weeks.add(List.copyOf(cells.subList(i, i + 7)));
        return weeks;
    }
    private final TransactionRepository repository;
    public ReportService(TransactionRepository repository) { this.repository = repository; }

    public List<Transaction> all() { return repository.search("", "", null, null); }

    public static Summary summarize(List<Transaction> rows) {
        long sales = 0, profit = 0, shipping = 0, fees = 0, purchases = 0, returnCosts = 0, returnCount = 0;
        for (Transaction row : rows) {
            sales += row.getRecordedSales();
            profit += row.getProfit();
            shipping += row.getRecordedShipping();
            fees += row.getRecordedFees();
            purchases += row.getRecordedPurchases();
            if (row.isReturned()) {
                returnCosts += row.getReturnCost();
                returnCount++;
            }
        }
        return new Summary(sales, profit, shipping, fees, purchases, rows.size() - returnCount, returnCosts, returnCount);
    }

    public List<Group> monthly(List<Transaction> rows, int year) {
        List<Group> groups = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            YearMonth target = YearMonth.of(year, month);
            groups.add(new Group(target.toString(), summarize(rows.stream().filter(t -> YearMonth.from(t.getSoldDate()).equals(target)).toList())));
        }
        return groups;
    }

    public List<Group> yearly(List<Transaction> rows) {
        Map<String, List<Transaction>> groups = new TreeMap<>(Comparator.reverseOrder());
        for (Transaction row : rows) groups.computeIfAbsent(String.valueOf(row.getSoldDate().getYear()), k -> new ArrayList<>()).add(row);
        return groups.entrySet().stream().map(e -> new Group(e.getKey(), summarize(e.getValue()))).toList();
    }

    public List<Group> byMarketplace(List<Transaction> rows) {
        Map<String, List<Transaction>> groups = new TreeMap<>();
        for (Transaction row : rows) groups.computeIfAbsent(row.getPlatformName(), k -> new ArrayList<>()).add(row);
        return groups.entrySet().stream().map(e -> new Group(e.getKey(), summarize(e.getValue()))).toList();
    }

    public List<Bar> chart(List<Group> groups) {
        double max = groups.stream().mapToDouble(g -> Math.abs((double) g.summary().profit())).max().orElse(0);
        return groups.stream().map(g -> new Bar(g.label(), g.summary().profit(), max == 0 ? 0 : Math.abs((double) g.summary().profit()) / max * 100)).toList();
    }
}
