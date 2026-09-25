package com.example.cashflow.service;

import com.example.cashflow.entity.Transaction;
import com.example.cashflow.repository.TransactionRepository;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ReportService {
    public record Summary(long sales, long profit, long shipping, long fees, long purchases, long count) {}
    public record Group(String label, Summary summary) {}
    public record Bar(String label, long profit, double width) {}
    private final TransactionRepository repository;
    public ReportService(TransactionRepository repository) { this.repository = repository; }

    public List<Transaction> all() { return repository.search("", "", null, null); }

    public static Summary summarize(List<Transaction> rows) {
        long sales = 0, profit = 0, shipping = 0, fees = 0, purchases = 0;
        for (Transaction row : rows) {
            sales += row.getSellingPrice();
            profit += row.getProfit();
            shipping += row.getShippingCost();
            fees += row.getSellingFee();
            purchases += row.getPurchasePrice() == null ? 0 : row.getPurchasePrice();
        }
        return new Summary(sales, profit, shipping, fees, purchases, rows.size());
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
