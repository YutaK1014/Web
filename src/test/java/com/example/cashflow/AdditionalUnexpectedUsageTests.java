package com.example.cashflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Characterization tests: successful assertions document unprotected operations.
 * Also runs the seven inherited save-protection checks against isolated H2.
 */
class AdditionalUnexpectedUsageTests extends SaveProtectionTests {
    @BeforeEach void clearTemplates() { jdbc.update("DELETE FROM shipping_templates"); }

    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder template(String url, String name, String cost) {
        return post(url).param("name", name).param("shippingMethod", "宅配便")
            .param("shippingCost", cost).param("packagingCost", "0");
    }

    @Test void staleSettingsOverwriteNewRatesAndGoals() throws Exception {
        mvc.perform(get("/settings")).andExpect(status().isOk());
        mvc.perform(get("/settings")).andExpect(status().isOk());
        mvc.perform(post("/settings").param("mercari", "20").param("rakuma", "6")
            .param("yahoo", "5").param("rounding", "UP").param("monthlyGoal", "50000"))
            .andExpect(status().is3xxRedirection());
        mvc.perform(post("/settings").param("mercari", "10").param("rakuma", "6")
            .param("yahoo", "5").param("rounding", "DOWN").param("monthlyGoal", "10000"))
            .andExpect(status().is3xxRedirection());
        assertEquals(0, new java.math.BigDecimal("10").compareTo(marketplaces.rate("メルカリ", "")));
        assertEquals("DOWN", marketplaces.rounding());
        assertEquals(10000L, marketplaces.monthlyGoal());
        mvc.perform(sale(saleId())).andExpect(status().is3xxRedirection());
        assertEquals(300, sales.search("", "", null, null).getFirst().getSellingFee());
    }

    @Test void replayingTemplateRegistrationCreatesDuplicates() throws Exception {
        for (int i = 0; i < 2; i++)
            mvc.perform(template("/settings/shipping-templates", "同じテンプレート", "230"))
                .andExpect(status().is3xxRedirection());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM shipping_templates", Integer.class));
    }

    @Test void staleTemplateEditOverwritesNewerCostAndName() throws Exception {
        mvc.perform(template("/settings/shipping-templates", "元の名前", "230")).andExpect(status().is3xxRedirection());
        long id = jdbc.queryForObject("SELECT id FROM shipping_templates", Long.class);
        String url = "/settings/shipping-templates/" + id + "/edit";
        mvc.perform(get(url)).andExpect(status().isOk());
        mvc.perform(get(url)).andExpect(status().isOk());
        mvc.perform(template(url, "更新した名前", "750")).andExpect(status().is3xxRedirection());
        mvc.perform(template(url, "元の名前", "230")).andExpect(status().is3xxRedirection());
        assertEquals(230, jdbc.queryForObject("SELECT shipping_cost FROM shipping_templates WHERE id=?", Integer.class, id));
        assertEquals("元の名前", jdbc.queryForObject("SELECT name FROM shipping_templates WHERE id=?", String.class, id));
    }

    @Test void nbspOnlyTemplateNameAndShippingMethodAreAccepted() throws Exception {
        mvc.perform(template("/settings/shipping-templates", "\u00a0", "230")
            .with(req -> { req.setParameter("shippingMethod", "\u00a0"); return req; }))
            .andExpect(status().is3xxRedirection());
        assertEquals("\u00a0", jdbc.queryForObject("SELECT name FROM shipping_templates", String.class));
        assertEquals("\u00a0", jdbc.queryForObject("SELECT shipping_method FROM shipping_templates", String.class));
    }

    @Test void nbspOnlyCustomMarketplaceAndTagAreAccepted() throws Exception {
        mvc.perform(sale(saleId()).with(req -> {
            req.setParameter("marketplace", "その他"); req.setParameter("customMarketplace", "\u00a0");
            req.setParameter("feeRate", "10"); req.setParameter("tags", "\u00a0"); return req;
        })).andExpect(status().is3xxRedirection());
        var row = sales.search("", "", null, null).getFirst();
        assertEquals("\u00a0", row.getPlatformName());
        assertEquals(java.util.List.of("\u00a0"), tags.transactionTags(row.getId()));
    }
}
