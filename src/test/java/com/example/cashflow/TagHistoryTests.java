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
    "spring.datasource.url=jdbc:h2:mem:tags;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password="
})
class TagHistoryTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired TagRepository tags;
    @Autowired PurchaseRepository purchases;
    @Autowired TransactionRepository transactions;
    MockMvc mvc;

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
}
