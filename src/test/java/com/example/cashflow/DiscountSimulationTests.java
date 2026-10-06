package com.example.cashflow;

import com.example.cashflow.dto.DiscountForm;
import com.example.cashflow.service.DiscountService;
import com.example.cashflow.service.MarketplaceService;
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
    "spring.datasource.url=jdbc:h2:mem:discount;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password="
})
class DiscountSimulationTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired DiscountService service;
    @Autowired MarketplaceService marketplaces;
    MockMvc mvc;

    @BeforeEach void setup() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        mvc.perform(post("/settings").param("mercari", "10").param("rakuma", "6")
            .param("yahoo", "5").param("rounding", "DOWN")).andExpect(status().is3xxRedirection());
    }

    DiscountForm form() {
        DiscountForm form = new DiscountForm();
        form.setSellingPrice("2000");
        form.setMarketplace("その他");
        form.setFeeRate("10");
        form.setShippingCost("200");
        form.setPurchasePrice("500");
        form.setOtherCost("100");
        form.setDesiredProfit("300");
        return form;
    }

    @Test void calculatesMinimumWithEveryRoundingModeAndRecalculatesFees() throws Exception {
        for (String mode : new String[]{"DOWN", "HALF_UP", "UP"}) {
            jdbc.update("UPDATE app_settings SET setting_value=? WHERE setting_key='fee_rounding'", mode);
            var result = service.calculate(form());
            assertEquals(200, result.currentFee());
            assertEquals(1000, result.currentProfit());
            int minimum = result.minimumPrice();
            assertEquals(mode.equals("UP") ? 1223 : 1222, minimum);
            assertEquals(2000 - minimum, result.discount());
            assertTrue(result.minimumProfit() >= 300);
            assertTrue(minimum - 1L - marketplaces.calculateFee(minimum - 1, result.rate()) - 800 < 300);
        }
    }

    @Test void handlesLossNoMarginZeroAndHundredPercentAndLargeCosts() {
        var form = form();
        form.setSellingPrice("100");
        var loss = service.calculate(form);
        assertEquals(-710, loss.currentProfit());
        assertEquals(1010, loss.shortfall());
        assertEquals(0, loss.discount());
        form.setSellingPrice("1222");
        assertEquals(0, service.calculate(form).discount());
        form.setFeeRate("0");
        assertEquals(1100, service.calculate(form).minimumPrice());
        form.setFeeRate("100");
        assertNull(service.calculate(form).minimumPrice());
        form.setShippingCost("0"); form.setPurchasePrice("0"); form.setOtherCost("0"); form.setDesiredProfit("0");
        assertEquals(0, service.calculate(form).minimumPrice());
        form.setSellingPrice("0");
        assertEquals(0, service.calculate(form).discount());
        form.setShippingCost("1000000000"); form.setPurchasePrice("1000000000");
        form.setOtherCost("1000000000"); form.setDesiredProfit("1000000000");
        var large = service.calculate(form);
        assertEquals(-3000000000L, large.currentProfit());
        assertEquals(4000000000L, large.shortfall());
        assertNull(large.minimumPrice());
    }

    @Test void rendersCalculationAndPreservesInputsWithoutSavingTransactions() throws Exception {
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Long.class);
        mvc.perform(get("/discount-simulator")).andExpect(status().isOk());
        mvc.perform(post("/discount-simulator").param("sellingPrice", "2000").param("marketplace", "メルカリ")
            .param("feeRate", "99").param("shippingCost", "200").param("purchasePrice", "500")
            .param("otherCost", "100").param("desiredProfit", "300"))
            .andExpect(status().isOk()).andExpect(model().hasNoErrors())
            .andExpect(content().string(containsString("778円")))
            .andExpect(content().string(containsString("1,222円")))
            .andExpect(content().string(containsString("value=\"2000\"")));
        mvc.perform(post("/discount-simulator").param("sellingPrice", "0").param("marketplace", "その他")
            .param("feeRate", "100").param("shippingCost", "1"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("現在の販売価格では赤字です")))
            .andExpect(content().string(containsString("希望利益を確保できる販売価格がありません")));
        assertEquals(count, jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Long.class));
    }

    @Test void validatesAmountsRatesAndMissingSettings() throws Exception {
        for (String field : new String[]{"sellingPrice", "shippingCost", "purchasePrice", "otherCost", "desiredProfit"}) {
            for (String value : new String[]{"", "-1", "1.5", "1e3", "1000000001", "99999999999999999999"}) {
                mvc.perform(post("/discount-simulator").param(field, value))
                    .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("discountForm", field))
                    .andExpect(model().attributeDoesNotExist("result"));
            }
        }
        for (String rate : new String[]{"", "-1", "100.01", "1.001", "NaN"}) {
            mvc.perform(post("/discount-simulator").param("sellingPrice", "1000")
                .param("marketplace", "その他").param("feeRate", rate))
                .andExpect(model().attributeHasFieldErrors("discountForm", "feeRate"));
        }
        jdbc.update("DELETE FROM marketplace_settings WHERE marketplace_name='メルカリ'");
        mvc.perform(post("/discount-simulator").param("sellingPrice", "1000").param("marketplace", "メルカリ"))
            .andExpect(model().hasErrors()).andExpect(content().string(containsString("手数料率を設定してください")));
        jdbc.update("DELETE FROM app_settings WHERE setting_key='fee_rounding'");
        mvc.perform(post("/discount-simulator").param("sellingPrice", "1000")
            .param("marketplace", "その他").param("feeRate", "10"))
            .andExpect(model().hasErrors()).andExpect(content().string(containsString("端数処理を選択してください")));
    }
}
