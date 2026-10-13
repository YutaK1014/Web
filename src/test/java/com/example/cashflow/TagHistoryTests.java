package com.example.cashflow;

import com.example.cashflow.dto.TagForm;
import com.example.cashflow.repository.TagRepository;
import com.example.cashflow.repository.PurchaseRepository;
import com.example.cashflow.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:tags;MODE=MySQL;DB_CLOSE_DELAY=-1;IGNORECASE=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password="
})
@org.springframework.context.annotation.Import(TagHistoryTests.QueryCountConfiguration.class)
class TagHistoryTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired TagRepository tags;
    @Autowired PurchaseRepository purchases;
    @Autowired TransactionRepository transactions;
    @Autowired com.example.cashflow.service.TransactionService transactionService;
    @Autowired com.example.cashflow.service.PurchaseService purchaseService;
    @Autowired QueryCounter queryCounter;
    MockMvc mvc;

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class QueryCountConfiguration {
        @org.springframework.context.annotation.Bean
        QueryCounter queryCounter() { return new QueryCounter(); }
    }

    @org.apache.ibatis.plugin.Intercepts(@org.apache.ibatis.plugin.Signature(
        type = org.apache.ibatis.executor.statement.StatementHandler.class, method = "prepare",
        args = {java.sql.Connection.class, Integer.class}))
    static class QueryCounter implements org.apache.ibatis.plugin.Interceptor {
        int count;
        @Override public Object intercept(org.apache.ibatis.plugin.Invocation invocation) throws Throwable {
            count++;
            return invocation.proceed();
        }
    }

    @Test void historySearchQueryCountDoesNotGrowWithNumberOfRows() throws Exception {
        var filter = new com.example.cashflow.dto.TransactionFilter();
        queryCounter.count = 0;
        assertTrue(transactionService.search(filter).isEmpty());
        assertEquals(1, queryCounter.count);
        queryCounter.count = 0;
        assertTrue(purchaseService.all().isEmpty());
        assertEquals(1, queryCounter.count);
        for (int i = 0; i < 12; i++) {
            String value = i % 2 == 0 ? "検索タグ, 別タグ" : "";
            purchase("購入" + i, value);
            sale("/transactions", "売却" + i, value);
            queryCounter.count = 0;
            var sales = transactionService.search(filter);
            assertEquals(i + 1, sales.size());
            assertEquals(2, queryCounter.count, "Sales should use two SQL statements regardless of row count");
            assertEquals(i % 2 == 0 ? 2 : 0, sales.getFirst().getTags().size());
            queryCounter.count = 0;
            var bought = purchaseService.all();
            assertEquals(i + 1, bought.size());
            assertEquals(2, queryCounter.count, "Purchases should use two SQL statements regardless of row count");
            assertEquals(i % 2 == 0 ? 2 : 0, bought.getFirst().getTags().size());
        }
        filter.setTag("検索タグ");
        queryCounter.count = 0;
        var sales = transactionService.search(filter);
        assertEquals(6, sales.size());
        assertEquals(2, queryCounter.count);
        assertTrue(sales.stream().allMatch(row -> row.getTags().contains("別タグ")));
        queryCounter.count = 0;
        assertEquals(6, purchaseService.search("検索タグ").size());
        assertEquals(2, queryCounter.count);
    }

    @Test void tagSearchRemainsCaseSensitiveOnCaseInsensitiveDatabase() throws Exception {
        purchase("大文字購入", "Gift, 追加タグ");
        purchase("小文字購入", "gift");
        sale("/transactions", "大文字売却", "Gift, 追加タグ");
        sale("/transactions", "小文字売却", "gift");
        assertEquals(2, purchases.search("Gift", null, null).size(), "DB should return case-insensitive candidates");
        assertEquals(List.of("大文字購入"), purchaseService.search("Gift").stream().map(p -> p.getItemName()).toList());
        var filter = new com.example.cashflow.dto.TransactionFilter();
        filter.setTag("Gift");
        var sales = transactionService.search(filter);
        assertEquals(1, sales.size());
        assertEquals("大文字売却", sales.getFirst().getItemName());
        assertTrue(sales.getFirst().getTags().contains("追加タグ"));
    }

    @Test void repositoryFiltersPurchasesByDateAndTagBeforeLoadingTags() throws Exception {
        long selected = purchase("期間内", "対象");
        long excluded = purchase("期間外", "対象");
        purchase("別タグ", "対象外");
        jdbc.update("UPDATE purchases SET purchased_date='2026-08-31' WHERE id=?", excluded);
        var rows = purchases.search("対象", java.time.LocalDate.of(2026, 9, 1), java.time.LocalDate.of(2026, 9, 30));
        assertEquals(List.of(selected), rows.stream().map(p -> p.getId()).toList());
    }

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        jdbc.update("DELETE FROM purchases");
        jdbc.update("DELETE FROM transactions");
        jdbc.update("DELETE FROM app_settings");
        jdbc.update("INSERT INTO app_settings(setting_key,setting_value) VALUES('fee_rounding','DOWN')");
    }

    long purchase(String name, String value) throws Exception {
        mvc.perform(post("/purchases").param("itemName", name).param("amount", "500")
            .param("purchasedDate", "2026-09-30").param("tags", value))
            .andExpect(status().is3xxRedirection());
        return purchases.findAll().getFirst().getId();
    }

    void sale(String url, String name, String value) throws Exception {
        mvc.perform(post(url).param("itemName", name).param("sellingPrice", "1000")
            .param("shippingCost", "100").param("marketplace", "その他").param("customMarketplace", "店舗")
            .param("feeRate", "10").param("soldDate", "2026-09-30").param("tags", value))
            .andExpect(status().is3xxRedirection());
    }

    @Test void purchaseTagsCanBeAddedToExistingHistorySearchedReplacedAndRemoved() throws Exception {
        long id = purchase("購入対象", "");
        purchase("対象外購入", "衣類小物");
        mvc.perform(post("/purchases/{id}/tags", id).param("tags", " 衣類, 衣類、プレゼント"))
            .andExpect(status().is3xxRedirection());
        assertEquals(2, tags.purchaseTags(id).size());
        mvc.perform(get("/purchases/{id}/tags", id)).andExpect(status().isOk())
            .andExpect(content().string(containsString("プレゼント")));
        mvc.perform(get("/").param("tag", " 衣類 ")).andExpect(status().isOk())
            .andExpect(content().string(containsString("購入対象")))
            .andExpect(content().string(not(containsString("対象外購入"))))
            .andExpect(content().string(containsString("タグを編集")));
        mvc.perform(post("/purchases/{id}/tags", id).param("tags", "新しいタグ"))
            .andExpect(status().is3xxRedirection());
        assertEquals(List.of("新しいタグ"), tags.purchaseTags(id));
        mvc.perform(post("/purchases/{id}/tags", id).param("tags", ""))
            .andExpect(status().is3xxRedirection());
        assertTrue(tags.purchaseTags(id).isEmpty());
        assertEquals(500, purchases.findById(id).getAmount());
    }

    @Test void salesSupportTagsInCreateEditCombinedSearchAndCsvAndCleanUpOnDelete() throws Exception {
        sale("/transactions", "売却対象", "衣類, プレゼント");
        long id = transactions.search("", "", null, null).getFirst().getId();
        sale("/transactions", "対象外売却", "衣類小物");
        mvc.perform(get("/transactions").param("tag", "衣類").param("keyword", "売却"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("売却対象")))
            .andExpect(content().string(not(containsString("対象外売却"))));
        mvc.perform(get("/transactions").param("tag", "衣類").param("month", "2026-08"))
            .andExpect(model().attribute("transactions", hasSize(0)));
        mvc.perform(get("/transactions/export").param("tag", "衣類"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("プレゼント")))
            .andExpect(content().string(not(containsString("対象外売却"))));
        mvc.perform(get("/transactions/{id}/edit", id)).andExpect(status().isOk())
            .andExpect(content().string(containsString("プレゼント")));
        sale("/transactions/" + id + "/edit", "売却対象", "更新タグ");
        assertEquals(List.of("更新タグ"), tags.transactionTags(id));
        sale("/transactions/" + id + "/edit", "売却対象", "");
        assertTrue(tags.transactionTags(id).isEmpty());
        sale("/transactions/" + id + "/edit", "売却対象", "削除対象");
        mvc.perform(post("/transactions/{id}/delete", id)).andExpect(status().is3xxRedirection());
        assertTrue(tags.transactionTags(id).isEmpty());
    }

    @Test void invalidTagsDoNotOverwriteExistingValuesAndMissingPurchasesReturn404() throws Exception {
        long id = purchase("商品", "保存済み");
        mvc.perform(post("/purchases/{id}/tags", id).param("tags", "a".repeat(31)))
            .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("tagForm", "tags"));
        assertEquals(List.of("保存済み"), tags.purchaseTags(id));
        mvc.perform(get("/purchases/999999/tags")).andExpect(status().isNotFound());
        mvc.perform(post("/purchases/999999/tags").param("tags", "test")).andExpect(status().isNotFound());
        mvc.perform(get("/transactions").param("tag", "a".repeat(31)))
            .andExpect(model().attributeHasFieldErrors("filter", "tag"));
        mvc.perform(get("/transactions/export").param("tag", "a".repeat(31))).andExpect(status().isBadRequest());
        mvc.perform(get("/").param("tag", "a".repeat(31))).andExpect(status().isBadRequest());
    }

    @Test void tagsAreLiteralEscapedAndSeparateBetweenKindsOfHistory() throws Exception {
        purchase("特殊文字購入", "%_ #& <b>");
        sale("/transactions", "特殊文字売却", "別のタグ");
        mvc.perform(get("/").param("tag", "%_ #& <b>"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("特殊文字購入")))
            .andExpect(content().string(containsString("&lt;b&gt;")));
        mvc.perform(get("/").param("tag", "%"))
            .andExpect(model().attribute("purchases", hasSize(0)));
        mvc.perform(get("/transactions").param("tag", "%_ #& <b>"))
            .andExpect(model().attribute("transactions", hasSize(0)));
    }

    @Test void parserEnforcesLimitsAndPreservesSpacesInsideTags() {
        assertEquals(List.of("夏 服", "ギフト"), TagForm.parse(" 夏 服、ギフト，夏 服,,"));
        assertTrue(TagForm.parse(null).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> TagForm.parse("a".repeat(31)));
        assertThrows(IllegalArgumentException.class, () -> TagForm.parse("a".repeat(1001)));
        assertThrows(IllegalArgumentException.class, () -> TagForm.parse("1,2,3,4,5,6,7,8,9,10,11"));
        assertEquals(10, TagForm.parse("1,2,3,4,5,6,7,8,9,10").size());
    }

    @Test void bothHistoriesSupportInclusiveDatePresetsCustomRangesAndTags() throws Exception {
        var today = java.time.LocalDate.now();
        var dates = List.of(today, today.minusMonths(1), today.minusMonths(3),
            today.minusMonths(3).minusDays(1), today.plusDays(1));
        for (int i = 0; i < dates.size(); i++) {
            long id = purchase("期間商品" + i, "衣類");
            jdbc.update("UPDATE purchases SET purchased_date=? WHERE id=?", dates.get(i), id);
            sale("/transactions", "期間商品" + i, "衣類");
            jdbc.update("UPDATE transactions SET sold_date=? WHERE item_name=?", dates.get(i), "期間商品" + i);
        }
        for (String url : List.of("/", "/transactions")) {
            String attribute = url.equals("/") ? "purchases" : "transactions";
            mvc.perform(get(url).param("period", "1month").param("tag", "衣類"))
                .andExpect(status().isOk()).andExpect(model().attribute(attribute, hasSize(2)));
            mvc.perform(get(url).param("period", "3months").param("tag", "衣類")
                .param("from", "2000-01-01").param("to", "2000-01-02").param("month", "2000-01"))
                .andExpect(status().isOk()).andExpect(model().attribute(attribute, hasSize(3)));
            mvc.perform(get(url).param("period", "custom").param("from", today.minusMonths(3).toString())
                .param("to", today.minusMonths(1).toString()).param("tag", "衣類"))
                .andExpect(status().isOk()).andExpect(model().attribute(attribute, hasSize(2)));
            mvc.perform(get(url).param("period", "1month").param("tag", "対象外"))
                .andExpect(model().attribute(attribute, hasSize(0)));
            mvc.perform(get(url)).andExpect(model().attribute(attribute, hasSize(5)));
        }
        mvc.perform(get("/transactions/export").param("period", "1month"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("期間商品0")))
            .andExpect(content().string(containsString("期間商品1")))
            .andExpect(content().string(not(containsString("期間商品2"))))
            .andExpect(content().string(not(containsString("期間商品4"))));
    }

    @Test void invalidPeriodsAndDatesAreRejectedWithVisibleErrors() throws Exception {
        for (String url : List.of("/", "/transactions")) {
            String form = url.equals("/") ? "purchaseFilter" : "filter";
            mvc.perform(get(url).param("period", "invalid"))
                .andExpect(model().attributeHasFieldErrors(form, "period"));
            mvc.perform(get(url).param("period", "custom").param("from", "2026-01-01"))
                .andExpect(model().attributeHasErrors(form))
                .andExpect(content().string(containsString("開始日と終了日を入力")));
            mvc.perform(get(url).param("period", "custom").param("from", "2026-03-01").param("to", "2026-02-01"))
                .andExpect(model().attributeHasErrors(form));
            mvc.perform(get(url).param("period", "custom").param("from", "2026-02-30").param("to", "2026-03-01"))
                .andExpect(model().attributeHasErrors(form));
        }
        mvc.perform(get("/transactions/export").param("period", "custom"))
            .andExpect(status().isBadRequest());
    }

    @Test void calendarMonthPresetsHandleMonthEndsAndLeapYears() {
        var range = new com.example.cashflow.dto.DateRangeFilter(java.time.LocalDate.of(2024, 3, 31));
        range.setPeriod("1month");
        assertEquals(java.time.LocalDate.of(2024, 2, 29), range.start());
        assertEquals(java.time.LocalDate.of(2024, 3, 31), range.end());
        range.setPeriod("3months");
        assertEquals(java.time.LocalDate.of(2023, 12, 31), range.start());
    }
}
