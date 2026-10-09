package com.example.cashflow;

import com.example.cashflow.entity.Transaction;
import com.example.cashflow.repository.TransactionRepository;
import com.example.cashflow.service.ReportService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:monthlyreport;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "app.photos.directory=./target/monthly-report-photos"
})
class MonthlyReportTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionRepository repository;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        jdbc.update("DELETE FROM transactions");
    }

    private Transaction sale(String date) {
        var row = new Transaction();
        row.setItemName("商品"); row.setMarketplace("メルカリ");
        row.setSellingPrice(3000); row.setFeeRate(BigDecimal.TEN); row.setSellingFee(300);
        row.setShippingCost(200); row.setPurchasePrice(1000);
        row.setSoldDate(LocalDate.parse(date));
        repository.insert(row);
        return row;
    }

    @Test void monthlyReportIncludesLeapDayAndReturnsButExcludesAdjacentMonths() throws Exception {
        sale("2024-01-31"); sale("2024-02-01"); sale("2024-02-29"); sale("2024-03-01");
        var returned = sale("2024-02-15");
        repository.saveReturn(returned.getId(), 4000);
        var result = mvc.perform(get("/reports/monthly").param("month", "2024-02"))
            .andExpect(status().isOk()).andExpect(view().name("monthly-report"))
            .andExpect(content().string(containsString("月次レポート_2024-02_もうけメモ")))
            .andExpect(content().string(containsString("7,000円")))
            .andExpect(content().string(containsString("-1,000円")))
            .andExpect(content().string(containsString("PDF保存・印刷")))
            .andReturn().getModelAndView();
        var summary = (ReportService.Summary) result.getModel().get("summary");
        assertEquals(6000, summary.sales()); assertEquals(7000, summary.expenses());
        assertEquals(-1000, summary.profit()); assertEquals(2, summary.count());
        assertEquals(1, summary.returnCount());
        @SuppressWarnings("unchecked")
        var bars = (List<ReportService.Bar>) result.getModel().get("chart");
        assertEquals(29, bars.size()); assertEquals(1500, bars.getLast().profit());
        assertEquals(-4000, bars.get(14).profit()); assertEquals(100, bars.get(14).width());
        assertEquals(summary.profit(), bars.stream().mapToLong(ReportService.Bar::profit).sum());
    }

    @Test void emptyMonthAndSupportedBoundariesRenderWithoutInvalidGraphWidths() throws Exception {
        for (String month : new String[]{"1000-01", "9999-12", "2026-02"}) {
            var result = mvc.perform(get("/reports/monthly").param("month", month))
                .andExpect(status().isOk()).andExpect(model().attribute("empty", true))
                .andExpect(content().string(containsString("対象月の取引はありません")))
                .andReturn().getModelAndView();
            @SuppressWarnings("unchecked")
            var bars = (List<ReportService.Bar>) result.getModel().get("chart");
            assertTrue(bars.stream().allMatch(b -> b.width() == 0 && b.profit() == 0));
        }
        mvc.perform(get("/reports/monthly")).andExpect(status().isOk());
    }

    @Test void invalidMonthsAreRejectedAndEntryPointsCarrySelectedMonth() throws Exception {
        for (String month : new String[]{"", "2026-13", "2026-2", "0999-12", "10000-01", "bad"})
            mvc.perform(get("/reports/monthly").param("month", month)).andExpect(status().isBadRequest());
        mvc.perform(get("/dashboard").param("month", "2024-02"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("/reports/monthly?month=2024-02")));
        mvc.perform(get("/reports").param("year", "2024"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("value=\"2024-01\"")));
    }
}
