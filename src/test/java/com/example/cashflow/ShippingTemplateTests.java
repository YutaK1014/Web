package com.example.cashflow;

import com.example.cashflow.repository.ShippingTemplateRepository;
import com.example.cashflow.repository.TransactionRepository;
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
    "spring.datasource.url=jdbc:h2:mem:shippingtemplates;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "app.photos.directory=./target/template-test-photos"
})
class ShippingTemplateTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired ShippingTemplateRepository templates;
    @Autowired TransactionRepository transactions;
    MockMvc mvc;

    @BeforeEach void setup() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        jdbc.update("DELETE FROM shipping_templates");
        jdbc.update("DELETE FROM transactions");
        mvc.perform(post("/settings").param("mercari", "10").param("rakuma", "6")
            .param("yahoo", "5").param("rounding", "DOWN")).andExpect(status().is3xxRedirection());
    }

    long create() throws Exception {
        mvc.perform(post("/settings/shipping-templates").param("name", " 小物用 ")
            .param("shippingMethod", "ゆうパケット").param("packaging", "封筒＋緩衝材")
            .param("shippingCost", "230").param("packagingCost", "30"))
            .andExpect(redirectedUrl("/settings/shipping-templates"));
        var row = templates.findAll().getFirst();
        assertEquals("小物用", row.getName());
        assertEquals(260, row.getTotalCost());
        return row.getId();
    }

    @Test void managesTemplatesAndLeavesRecordedCostsUnchanged() throws Exception {
        mvc.perform(get("/settings/shipping-templates")).andExpect(status().isOk())
            .andExpect(content().string(containsString("テンプレートはまだありません")));
        long id = create();
        mvc.perform(get("/settings/shipping-templates")).andExpect(status().isOk())
            .andExpect(content().string(containsString("封筒＋緩衝材")));
        mvc.perform(get("/sales/register")).andExpect(status().isOk())
            .andExpect(content().string(containsString("data-total=\"260\"")));
        mvc.perform(multipart("/transactions").param("itemName", "小物").param("sellingPrice", "1000")
            .param("shippingCost", "260").param("marketplace", "メルカリ").param("soldDate", "2026-10-06"))
            .andExpect(redirectedUrl("/transactions"));
        var sale = transactions.search("", "", null, null).getFirst();
        assertEquals(640, sale.getProfit());
        mvc.perform(get("/settings/shipping-templates/" + id + "/edit")).andExpect(status().isOk());
        mvc.perform(post("/settings/shipping-templates/" + id + "/edit").param("name", "変更後")
            .param("shippingMethod", "宅配便").param("shippingCost", "750").param("packagingCost", "50"))
            .andExpect(redirectedUrl("/settings/shipping-templates"));
        assertEquals(800, templates.findById(id).getTotalCost());
        mvc.perform(get("/transactions/" + sale.getId() + "/edit")).andExpect(status().isOk())
            .andExpect(content().string(containsString("value=\"260\"")))
            .andExpect(content().string(containsString("data-total=\"800\"")));
        mvc.perform(get("/settings/shipping-templates/" + id + "/delete")).andExpect(status().isOk());
        assertNotNull(templates.findById(id));
        mvc.perform(post("/settings/shipping-templates/" + id + "/delete")).andExpect(status().is3xxRedirection());
        assertTrue(templates.findAll().isEmpty());
        assertEquals(260, transactions.findById(sale.getId()).getShippingCost());
        assertEquals(640, transactions.findById(sale.getId()).getProfit());
        mvc.perform(get("/settings/shipping-templates/" + id + "/edit")).andExpect(status().isNotFound());
        mvc.perform(post("/settings/shipping-templates/" + id + "/delete")).andExpect(status().isNotFound());
    }

    @Test void rejectsInvalidAmountsAndTextWithoutLosingInput() throws Exception {
        for (String value : new String[]{"", "-1", "1.5", "1000000001", "99999999999999999", "abc"}) {
            mvc.perform(post("/settings/shipping-templates").param("name", "入力保持")
                .param("shippingMethod", "配送").param("shippingCost", value).param("packagingCost", "0"))
                .andExpect(model().attributeHasFieldErrors("templateForm", "shippingCost"))
                .andExpect(content().string(containsString("入力保持")));
            mvc.perform(post("/settings/shipping-templates").param("name", "入力保持")
                .param("shippingMethod", "配送").param("shippingCost", "0").param("packagingCost", value))
                .andExpect(model().attributeHasFieldErrors("templateForm", "packagingCost"));
        }
        mvc.perform(post("/settings/shipping-templates").param("name", " ").param("shippingMethod", " ")
            .param("packaging", "a".repeat(101)).param("shippingCost", "1000000000").param("packagingCost", "1"))
            .andExpect(model().attributeHasFieldErrors("templateForm", "name", "shippingMethod", "packaging", "packagingCost"));
        assertTrue(templates.findAll().isEmpty());
        long id = create();
        mvc.perform(post("/settings/shipping-templates/" + id + "/edit").param("name", "変更失敗"))
            .andExpect(model().hasErrors());
        assertEquals("小物用", templates.findById(id).getName());
        mvc.perform(multipart("/transactions").param("shippingCost", "270"))
            .andExpect(model().hasErrors()).andExpect(content().string(containsString("value=\"270\"")))
            .andExpect(content().string(containsString("data-total=\"260\"")));
    }

    @Test void acceptsZeroAndMaximumAndEscapesTemplateText() throws Exception {
        for (String value : new String[]{"0", "1000000000"}) {
            mvc.perform(post("/settings/shipping-templates").param("name", "<script>alert(1)</script>")
                .param("shippingMethod", "配送").param("shippingCost", value).param("packagingCost", "0"))
                .andExpect(status().is3xxRedirection());
        }
        mvc.perform(get("/settings/shipping-templates")).andExpect(status().isOk())
            .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")));
        mvc.perform(get("/sales/register")).andExpect(status().isOk())
            .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")));
    }
}
