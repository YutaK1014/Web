package com.example.cashflow;

import com.example.cashflow.dto.ReturnForm;
import com.example.cashflow.entity.Transaction;
import com.example.cashflow.repository.TransactionRepository;
import com.example.cashflow.service.ReportService;
import com.example.cashflow.service.TransactionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:returns;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "app.photos.directory=./target/return-test-photos"
})
class TransactionReturnTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionRepository repository;
    @Autowired TransactionService service;
    @Autowired ReportService reports;
    MockMvc mvc;
    long id;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        jdbc.update("DELETE FROM transactions");
        jdbc.update("DELETE FROM marketplace_settings");
        jdbc.update("DELETE FROM app_settings");
        var row = new Transaction();
        row.setItemName("返品商品<script>"); row.setMarketplace("メルカリ");
        row.setSellingPrice(3000); row.setFeeRate(BigDecimal.TEN); row.setSellingFee(300);
        row.setShippingCost(750); row.setPurchasePrice(1000);
        row.setSoldDate(LocalDate.of(2026, 1, 2)); row.setMemo("残すメモ");
        row.setImagePath("/images/retained.png"); repository.insert(row); id = row.getId();
        jdbc.update("INSERT INTO transaction_tags(transaction_id, tag) VALUES(?, ?)", id, "衣類");
    }

    @Test void returnNeedsOnlyCostAndKeepsOriginalDataWithoutSettings() throws Exception {
        mvc.perform(get("/transactions/{id}/return", id)).andExpect(status().isOk())
            .andExpect(content().string(containsString("返品商品&lt;script&gt;")));
        assertFalse(repository.findById(id).isReturned());
        mvc.perform(post("/transactions/{id}/return", id).param("returnCost", "1200"))
            .andExpect(redirectedUrl("/transactions"));
        var row = service.get(id);
        assertTrue(row.isReturned()); assertEquals(-1200, row.getProfit());
        assertEquals(3000, row.getSellingPrice()); assertEquals(300, row.getSellingFee());
        assertEquals(750, row.getShippingCost()); assertEquals(1000, row.getPurchasePrice());
        assertEquals("/images/retained.png", row.getImagePath()); assertEquals("残すメモ", row.getMemo());
        assertEquals(List.of("衣類"), row.getTags());
        assertEquals(950, jdbc.queryForObject("SELECT profit FROM transactions WHERE id=?", Integer.class, id));
        mvc.perform(get("/transactions/{id}/edit", id)).andExpect(redirectedUrl("/transactions/" + id + "/return"));
        mvc.perform(get("/transactions/{id}/return", id)).andExpect(status().isOk())
            .andExpect(content().string(containsString("value=\"1200\"")));
        mvc.perform(get("/transactions").param("tag", "衣類")).andExpect(status().isOk())
            .andExpect(content().string(containsString("返品費用を編集")))
            .andExpect(content().string(containsString("-1,200円")));
        mvc.perform(get("/transactions/export").param("tag", "衣類"))
            .andExpect(content().string(containsString("状態,返品費用")))
            .andExpect(content().string(containsString(",0,10.00,0,0,0,-1200,2026-01-02,")))
            .andExpect(content().string(containsString("\"返品\",1200")));
    }

    @Test void replacesCostIncludingZeroAndRejectsInvalidAmountsWithoutChangingSavedCost() throws Exception {
        for (String value : new String[]{"1000000000", "1000000000", "0"}) {
            mvc.perform(post("/transactions/{id}/return", id).param("returnCost", value))
                .andExpect(redirectedUrl("/transactions"));
            assertEquals(-Long.parseLong(value), repository.findById(id).getProfit());
        }
        assertTrue(repository.findById(id).isReturned());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM transaction_returns", Integer.class));
        for (String value : new String[]{"", "-1", "1.5", "1000000001", "999999999999999999999999", "abc", "１", " "}) {
            mvc.perform(post("/transactions/{id}/return", id).param("returnCost", value))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "returnCost"));
            assertEquals(0, repository.findById(id).getReturnCost());
        }
        mvc.perform(post("/transactions/{id}/return", id)).andExpect(model().attributeHasFieldErrors("form", "returnCost"));
        var invalid = new ReturnForm(); invalid.setReturnCost("-1");
        assertThrows(IllegalArgumentException.class, () -> service.saveReturn(id, invalid));
    }

    @Test void aggregatesReturnsWithoutDoubleCountingAndShowsReturnOnlyCalendarDay() throws Exception {
        mvc.perform(post("/transactions/{id}/return", id).param("returnCost", "1200"));
        var rows = reports.all();
        var summary = ReportService.summarize(rows);
        assertEquals(0, summary.sales()); assertEquals(0, summary.fees());
        assertEquals(0, summary.shipping()); assertEquals(0, summary.purchases());
        assertEquals(0, summary.count()); assertEquals(1, summary.returnCount());
        assertEquals(1200, summary.returnCosts()); assertEquals(-1200, summary.profit());
        assertEquals(-1200, reports.monthly(rows, 2026).getFirst().summary().profit());
        assertEquals(-1200, reports.yearly(rows).getFirst().summary().profit());
        assertEquals(-1200, reports.byMarketplace(rows).getFirst().summary().profit());
        var day = reports.calendar(rows, YearMonth.of(2026, 1)).stream().flatMap(List::stream)
            .filter(d -> LocalDate.of(2026, 1, 2).equals(d.date())).findFirst().orElseThrow();
        assertEquals(0, day.count()); assertEquals(1, day.returnCount()); assertEquals(-1200, day.profit());
        mvc.perform(get("/dashboard").param("month", "2026-01")).andExpect(status().isOk())
            .andExpect(content().string(containsString("返品1件")))
            .andExpect(content().string(containsString("-1,200円")));
        mvc.perform(get("/reports").param("year", "2026")).andExpect(status().isOk())
            .andExpect(content().string(containsString("返品費用")));
        var sale = new Transaction(); sale.setSellingPrice(2000); sale.setSellingFee(200);
        sale.setShippingCost(300); sale.setPurchasePrice(100);
        var mixed = ReportService.summarize(List.of(rows.getFirst(), sale));
        assertEquals(2000, mixed.sales()); assertEquals(200, mixed.profit());
        assertEquals(1, mixed.count()); assertEquals(1, mixed.returnCount());
    }

    @Test void nonexistentTransactionsAre404AndExplicitDeletionCascadesReturn() throws Exception {
        mvc.perform(get("/transactions/999999/return")).andExpect(status().isNotFound());
        mvc.perform(post("/transactions/999999/return").param("returnCost", "0")).andExpect(status().isNotFound());
        mvc.perform(post("/transactions/{id}/return", id).param("returnCost", "0"));
        mvc.perform(post("/transactions/{id}/delete", id)).andExpect(redirectedUrl("/transactions"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM transaction_returns", Integer.class));
    }
}
