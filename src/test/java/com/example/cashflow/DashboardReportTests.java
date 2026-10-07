package com.example.cashflow;

import com.example.cashflow.entity.Transaction;
import com.example.cashflow.repository.TransactionRepository;
import com.example.cashflow.service.ReportService;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class DashboardReportTests {
    private final ReportService reports = new ReportService(mock(TransactionRepository.class));

    @Test void comparisonRatesHandleIncreasesDecreasesAndUnavailableBases() {
        var increase = new ReportService.Comparison("利益", "円", 200, 150);
        assertEquals(new BigDecimal("33.3"), increase.percentageChange());
        assertEquals("増加", increase.trend());
        var loss = new ReportService.Comparison("利益", "円", -50, 100);
        assertEquals(new BigDecimal("-150.0"), loss.percentageChange());
        assertEquals("減少", loss.trend());
        assertEquals(new BigDecimal("0.0"), new ReportService.Comparison("利益", "円", 100, 100).percentageChange());
        assertEquals("変化なし", new ReportService.Comparison("利益", "円", 0, 0).trend());
        assertNull(new ReportService.Comparison("利益", "円", 100, 0).percentageChange());
        var recovery = new ReportService.Comparison("利益", "円", -50, -100);
        assertNull(recovery.percentageChange());
        assertEquals("増加", recovery.trend());
        assertEquals(new BigDecimal("16.7"), new ReportService.Comparison("件数", "件", 7, 6).percentageChange());
    }

    @Test void comparisonIncludesReturnCostsButExcludesReturnsFromSalesCount() {
        var returned = row("2026-01-15", 5000, 0);
        returned.setReturnCost(300);
        var comparisons = reports.compare(List.of(row("2025-12-31", 1000, 0),
            row("2026-01-01", 800, 0), returned), YearMonth.of(2026, 1));
        assertEquals(500, comparisons.get(0).current());
        assertEquals(new BigDecimal("-50.0"), comparisons.get(0).percentageChange());
        assertEquals(1, comparisons.get(1).current());
        assertEquals("変化なし", comparisons.get(1).trend());
        assertEquals(500, comparisons.get(2).current());
    }

    private Transaction row(String date, int price, int shipping) {
        var row = new Transaction();
        row.setSoldDate(LocalDate.parse(date));
        row.setSellingPrice(price);
        row.setShippingCost(shipping);
        return row;
    }

    @Test void comparesAcrossYearBoundaryAndRoundsAverages() {
        var rows = List.of(row("2025-12-31", 0, 300), row("2026-01-01", 1000, 0),
            row("2026-01-31", 501, 0), row("2026-02-01", 9999, 0));
        var comparisons = reports.compare(rows, YearMonth.of(2026, 1));
        assertEquals(1801, comparisons.get(0).difference());
        assertEquals(1, comparisons.get(1).difference());
        assertEquals(751, comparisons.get(2).current());
        assertEquals(1051, comparisons.get(2).difference());
    }

    @Test void emptyMonthsAndZeroProfitSalesAreDistinct() {
        var rows = List.of(row("2026-02-01", 100, 100));
        var comparisons = reports.compare(rows, YearMonth.of(2026, 2));
        assertEquals(0, comparisons.get(2).previous());
        assertEquals(0, comparisons.get(2).current());
        assertEquals(1, comparisons.get(1).difference());
        var week = reports.calendar(rows, YearMonth.of(2026, 2)).getFirst();
        assertEquals(1, week.getFirst().count());
        assertEquals(0, week.getFirst().profit());
        assertEquals(0, week.get(1).count());
        assertTrue(reports.compare(List.of(), YearMonth.of(2026, 1)).stream()
            .allMatch(c -> c.current() == 0 && c.previous() == 0 && c.difference() == 0));
    }

    @Test void leapDayAggregatesSalesAndLossesWithoutAdjacentMonth() {
        var rows = List.of(row("2024-02-29", 500, 0), row("2024-02-29", 0, 900),
            row("2024-03-01", 9999, 0));
        var weeks = reports.calendar(rows, YearMonth.of(2024, 2));
        assertEquals(5, weeks.size());
        assertNull(weeks.getFirst().getFirst().date());
        assertEquals(LocalDate.of(2024, 2, 1), weeks.getFirst().get(4).date());
        var leapDay = weeks.getLast().get(4);
        assertEquals(LocalDate.of(2024, 2, 29), leapDay.date());
        assertEquals(2, leapDay.count());
        assertEquals(-400, leapDay.profit());
        assertNull(weeks.getLast().get(5).date());
        assertEquals(29, weeks.stream().flatMap(List::stream).filter(d -> d.date() != null).count());
    }

    @Test void calendarSupportsFourAndSixWeeksAndLargeTotals() {
        assertEquals(4, reports.calendar(List.of(), YearMonth.of(2026, 2)).size());
        var rows = List.of(row("2026-08-31", 1_000_000_000, 0),
            row("2026-08-31", 1_000_000_000, 0), row("2026-08-31", 1_000_000_000, 0));
        var weeks = reports.calendar(rows, YearMonth.of(2026, 8));
        assertEquals(6, weeks.size());
        assertEquals(3_000_000_000L, weeks.getLast().get(1).profit());
    }
}
