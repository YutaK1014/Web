package com.example.cashflow;

import com.example.cashflow.dto.SettingsForm;
import com.example.cashflow.repository.TransactionRepository;
import com.example.cashflow.service.MarketplaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Regression coverage for unexpected input and stale/replayed forms. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:unexpected;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "app.photos.directory=./target/unexpected-photos"
})
class UnexpectedUsageTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionRepository repository;
    @Autowired MarketplaceService marketplaces;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = CsrfTestSupport.mvc(context);
        jdbc.update("DELETE FROM transactions");
        jdbc.update("DELETE FROM purchases");
        var settings = new SettingsForm();
        settings.setMercari("10"); settings.setRakuma("6"); settings.setYahoo("5");
        settings.setRounding("DOWN"); settings.setVersion(marketplaces.settings().getVersion()); marketplaces.save(settings);
    }

    MockMultipartHttpServletRequestBuilder sale(String url, String name, String price) {
        return multipart(url).param("submissionId", java.util.UUID.randomUUID().toString())
            .param("version", "0").param("itemName", name).param("sellingPrice", price)
            .param("shippingCost", "750").param("purchasePrice", "1000")
            .param("marketplace", "メルカリ").param("soldDate", "2026-10-14");
    }
    long create() throws Exception {
        mvc.perform(sale("/transactions", "original", "3000")).andExpect(status().is3xxRedirection());
        return repository.search("", "", null, null).getFirst().getId();
    }

    @Test void replayingSameSaleAndDraftRevisionCreatesOnlyOneRow() throws Exception {
        String submission = java.util.UUID.randomUUID().toString();
        for (int i = 0; i < 2; i++)
            mvc.perform(sale("/transactions", "replayed", "3000").param("draftRevision", "same-revision")
                .with(req -> { req.setParameter("submissionId", submission); return req; }))
                .andExpect(status().is(i == 0 ? 302 : 409));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class));
        assertEquals(950L, jdbc.queryForObject("SELECT SUM(profit) FROM transactions", Long.class));
    }

    @Test void replayingPurchaseCreatesOnlyOneRow() throws Exception {
        String submission = java.util.UUID.randomUUID().toString();
        for (int i = 0; i < 2; i++)
            mvc.perform(post("/purchases").param("submissionId", submission).param("itemName", "replayed purchase")
                .param("purchasedDate", "2026-10-14").param("amount", "1000")
                .param("draftRevision", "same-revision")).andExpect(status().is(i == 0 ? 302 : 409));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM purchases", Integer.class));
    }

    @Test void staleTabCannotOverwriteNewerPriceAndTags() throws Exception {
        long id = create();
        String url = "/transactions/" + id + "/edit";
        mvc.perform(get(url)).andExpect(status().isOk()); // Tab A
        mvc.perform(get(url)).andExpect(status().isOk()); // Tab B
        mvc.perform(sale(url, "original", "4000").param("tags", "new tag"))
            .andExpect(status().is3xxRedirection());
        mvc.perform(sale(url, "renamed in stale tab", "3000"))
            .andExpect(status().isConflict()).andExpect(model().hasErrors());
        assertEquals(4000, repository.findById(id).getSellingPrice());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM transaction_tags WHERE transaction_id=?", Integer.class, id));
    }

    @Test void settingsChangedAfterOpeningFormAffectSavedFee() throws Exception {
        long id = create();
        String url = "/transactions/" + id + "/edit";
        mvc.perform(get(url)).andExpect(status().isOk());
        mvc.perform(post("/settings").param("version", context.getBean(com.example.cashflow.service.MarketplaceService.class).settings().getVersion().toString()).param("mercari", "20").param("rakuma", "6")
            .param("yahoo", "5").param("rounding", "DOWN")).andExpect(status().is3xxRedirection());
        mvc.perform(sale(url, "original", "3000").param("feeRate", "10"))
            .andExpect(status().is3xxRedirection());
        assertEquals(600, repository.findById(id).getSellingFee());
        assertEquals(650, repository.findById(id).getProfit());
    }

    @Test void staleEditAfterReturnCannotRestoreSale() throws Exception {
        long id = create();
        mvc.perform(post("/transactions/" + id + "/return").param("version", "0").param("returnCost", "500"))
            .andExpect(status().is3xxRedirection());
        mvc.perform(sale("/transactions/" + id + "/edit", "stale", "4000"))
            .andExpect(status().isOk()).andExpect(model().hasErrors());
        assertEquals("original", repository.findById(id).getItemName());
        assertEquals(-500, repository.findById(id).getProfit());
    }

    @Test void staleEditAndRepeatedDeleteReturn404() throws Exception {
        long id = create();
        mvc.perform(post("/transactions/" + id + "/delete").param("version", "0")).andExpect(status().is3xxRedirection());
        mvc.perform(sale("/transactions/" + id + "/edit", "stale", "4000"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/transactions/" + id + "/delete").param("version", "0")).andExpect(status().isNotFound());
        assertNull(repository.findById(id));
    }

    @Test void visuallyBlankNonBreakingSpaceIsRejectedAsName() throws Exception {
        mvc.perform(sale("/transactions", "\u00a0", "3000")).andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("form", "itemName"));
        assertTrue(repository.search("", "", null, null).isEmpty());
    }

    @Test void bypassedHtmlAmountConstraintsDoNotWrite() throws Exception {
        for (String value : new String[]{"-1", "1e3", "１０００", "1,000", " 1000 ", "1000000001", "9".repeat(500)}) {
            mvc.perform(sale("/transactions", "invalid", value)).andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "sellingPrice"));
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class));
    }

    @Test void forgedCalculatedAndIdentityFieldsAreIgnored() throws Exception {
        long id = create();
        mvc.perform(sale("/transactions", "forged", "3000").param("id", Long.toString(id))
            .param("profit", "999999").param("sellingFee", "0").param("returnCost", "0")
            .param("imagePath", "/images/forged.png")).andExpect(status().is3xxRedirection());
        var rows = repository.search("", "", null, null);
        assertEquals(2, rows.size());
        var row = rows.stream().filter(r -> r.getItemName().equals("forged")).findFirst().orElseThrow();
        assertEquals(950, row.getProfit()); assertEquals(300, row.getSellingFee());
        assertFalse(row.isReturned()); assertNull(row.getImagePath());
    }

    @Test void fakeImageDoesNotPartiallySaveTransaction() throws Exception {
        mvc.perform(sale("/transactions", "fake image", "3000").file(
            new MockMultipartFile("photo", "photo.png", "image/png", "not a PNG".getBytes())))
            .andExpect(status().isOk()).andExpect(model().hasErrors());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class));
    }

    @Test void invalidIdsAndReportInputsAreRejectedWithoutServerError() throws Exception {
        for (String id : new String[]{"abc", "999999999999999999999999"})
            mvc.perform(get("/transactions/" + id + "/edit")).andExpect(status().isBadRequest());
        for (String month : new String[]{"", "2026-13", "0999-12", "+10000-01", "2026-01-01"}) {
            mvc.perform(get("/dashboard").param("month", month)).andExpect(status().isBadRequest());
            mvc.perform(get("/reports/monthly").param("month", month)).andExpect(status().isBadRequest());
        }
    }
}
