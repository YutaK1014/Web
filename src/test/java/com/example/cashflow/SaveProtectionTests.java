package com.example.cashflow;

import com.example.cashflow.dto.*;
import com.example.cashflow.repository.PurchaseRepository;
import com.example.cashflow.repository.TagRepository;
import com.example.cashflow.repository.TransactionRepository;
import com.example.cashflow.service.MarketplaceService;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:save-protection;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "app.photos.directory=./target/save-protection-photos"
})
class SaveProtectionTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionRepository sales;
    @Autowired PurchaseRepository purchases;
    @Autowired TagRepository tags;
    @Autowired MarketplaceService marketplaces;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        jdbc.update("DELETE FROM transactions");
        jdbc.update("DELETE FROM purchases");
        jdbc.update("DELETE FROM registration_submissions");
        var settings = new SettingsForm();
        settings.setMercari("10"); settings.setRakuma("6"); settings.setYahoo("5");
        settings.setRounding("DOWN"); marketplaces.save(settings);
    }

    String saleId() throws Exception {
        var result = mvc.perform(get("/sales/register")).andExpect(status().isOk()).andReturn();
        return ((TransactionForm) result.getModelAndView().getModel().get("form")).getSubmissionId();
    }

    String purchaseId() throws Exception {
        var result = mvc.perform(get("/")).andExpect(status().isOk()).andReturn();
        return ((PurchaseForm) result.getModelAndView().getModel().get("purchaseForm")).getSubmissionId();
    }

    MockHttpServletRequestBuilder sale(String token) {
        return saleFields(post("/transactions")).param("submissionId", token);
    }

    <B extends org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder<B>> B saleFields(B request) {
        return request.param("itemName", "商品").param("sellingPrice", "3000")
            .param("marketplace", "メルカリ").param("shippingCost", "750")
            .param("purchasePrice", "1000").param("soldDate", "2026-10-14").param("tags", "元のタグ");
    }

    MockHttpServletRequestBuilder purchase(String token) {
        return post("/purchases").param("submissionId", token).param("itemName", "商品")
            .param("purchasedDate", "2026-10-14").param("amount", "1000").param("tags", "元のタグ");
    }

    void assertSingleWinner(Callable<Integer> first, Callable<Integer> second) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = List.of(first, second).stream().map(action -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("start timeout");
                return action.call();
            })).toList();
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            var statuses = List.of(futures.get(0).get(15, TimeUnit.SECONDS), futures.get(1).get(15, TimeUnit.SECONDS));
            assertEquals(List.of(302, 409), statuses.stream().sorted().toList());
        }
    }

    @Test void simultaneousSaleAndPurchaseReplaysEachSaveOnce() throws Exception {
        String saleToken = saleId(), purchaseToken = purchaseId();
        assertSingleWinner(() -> mvc.perform(sale(saleToken)).andReturn().getResponse().getStatus(),
            () -> mvc.perform(sale(saleToken)).andReturn().getResponse().getStatus());
        assertSingleWinner(() -> mvc.perform(purchase(purchaseToken)).andReturn().getResponse().getStatus(),
            () -> mvc.perform(purchase(purchaseToken)).andReturn().getResponse().getStatus());
        assertEquals(1, sales.search("", "", null, null).size());
        assertEquals(950L, jdbc.queryForObject("SELECT SUM(profit) FROM transactions", Long.class));
        assertEquals(1, purchases.findAll().size());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM registration_submissions", Integer.class));
    }

    @Test void separateNewFormsAllowIntentionalIdenticalTransactions() throws Exception {
        String first = saleId(), second = saleId();
        assertNotEquals(first, second);
        mvc.perform(sale(first)).andExpect(status().is3xxRedirection());
        mvc.perform(sale(second)).andExpect(status().is3xxRedirection());
        assertEquals(2, sales.search("", "", null, null).size());
        mvc.perform(purchase(purchaseId())).andExpect(status().is3xxRedirection());
        mvc.perform(purchase(purchaseId())).andExpect(status().is3xxRedirection());
        assertEquals(2, purchases.findAll().size());
    }

    @Test void missingOrInvalidSubmissionIdsCannotBypassProtection() throws Exception {
        for (String token : new String[]{"", "bad-token", "x".repeat(200)}) {
            mvc.perform(sale(token)).andExpect(status().isConflict());
            mvc.perform(purchase(token)).andExpect(status().isConflict());
        }
        mvc.perform(saleFields(post("/transactions"))).andExpect(status().isConflict());
        assertTrue(sales.search("", "", null, null).isEmpty());
        assertTrue(purchases.findAll().isEmpty());
    }

    @Test void validationAndPhotoFailuresAllowRetryWithOriginalSubmissionId() throws Exception {
        String token = saleId();
        var invalid = mvc.perform(sale(token).with(req -> { req.setParameter("itemName", "\u00a0"); return req; }))
            .andExpect(model().attributeHasFieldErrors("form", "itemName")).andReturn();
        assertEquals(token, ((TransactionForm) invalid.getModelAndView().getModel().get("form")).getSubmissionId());
        mvc.perform(saleFields(multipart("/transactions").file(new MockMultipartFile("photo", "bad.png", "image/png", new byte[]{1, 2})))
            .param("submissionId", token)).andExpect(model().hasErrors());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM registration_submissions", Integer.class));
        mvc.perform(sale(token)).andExpect(status().is3xxRedirection());
        var replay = mvc.perform(sale(token)).andExpect(status().isConflict()).andReturn();
        assertFalse(replay.getFlashMap().containsKey("savedDraftKey"));
        assertEquals(1, sales.search("", "", null, null).size());
    }

    @Test void concurrentEditsProtectPriceTagsAndVersionAndPermitFreshEdit() throws Exception {
        mvc.perform(sale(saleId())).andExpect(status().is3xxRedirection());
        long id = sales.search("", "", null, null).getFirst().getId();
        String url = "/transactions/" + id + "/edit";
        var page = mvc.perform(get(url)).andReturn();
        Long version = ((TransactionForm) page.getModelAndView().getModel().get("form")).getVersion();
        Callable<Integer> first = () -> mvc.perform(saleFields(post(url)).param("version", version.toString())
            .with(req -> { req.setParameter("sellingPrice", "4000"); req.setParameter("tags", "A"); return req; }))
            .andReturn().getResponse().getStatus();
        Callable<Integer> second = () -> mvc.perform(saleFields(post(url)).param("version", version.toString())
            .with(req -> { req.setParameter("sellingPrice", "5000"); req.setParameter("tags", "B"); return req; }))
            .andReturn().getResponse().getStatus();
        assertSingleWinner(first, second);
        var saved = sales.findById(id);
        assertEquals(1, saved.getVersion());
        assertEquals(List.of(saved.getSellingPrice() == 4000 ? "A" : "B"), tags.transactionTags(id));
        var stale = mvc.perform(saleFields(post(url)).param("version", version.toString()))
            .andExpect(status().isConflict()).andReturn();
        assertFalse(stale.getFlashMap().containsKey("savedDraftKey"));
        assertEquals(version, ((TransactionForm) stale.getModelAndView().getModel().get("form")).getVersion());
        mvc.perform(saleFields(post(url))).andExpect(status().isConflict());
        mvc.perform(saleFields(post(url)).param("version", Long.toString(saved.getVersion())))
            .andExpect(status().is3xxRedirection());
        assertEquals(2, sales.findById(id).getVersion());
    }

    @Test void simultaneousPurchaseTagEditsHaveOneWinner() throws Exception {
        mvc.perform(purchase(purchaseId())).andExpect(status().is3xxRedirection());
        long id = purchases.findAll().getFirst().getId();
        String url = "/purchases/" + id + "/tags";
        var page = mvc.perform(get(url)).andReturn();
        var form = (TagForm) page.getModelAndView().getModel().get("tagForm");
        assertSingleWinner(() -> mvc.perform(post(url).param("version", form.getVersion().toString()).param("tags", "A"))
                .andReturn().getResponse().getStatus(),
            () -> mvc.perform(post(url).param("version", form.getVersion().toString()).param("tags", "B"))
                .andReturn().getResponse().getStatus());
        assertEquals(1, purchases.findById(id).getVersion());
        assertEquals(1, tags.purchaseTags(id).size());
        mvc.perform(post(url).param("tags", "" )).andExpect(status().isConflict());
        assertFalse(tags.purchaseTags(id).isEmpty());
    }

    @Test void unicodeWhitespaceOnlyNamesAreRejectedForSalePurchaseAndEdit() throws Exception {
        mvc.perform(sale(saleId())).andExpect(status().is3xxRedirection());
        long id = sales.search("", "", null, null).getFirst().getId();
        for (String name : new String[]{"\u00a0", "\u2007", "\u202f", " \t\u3000\u00a0\n"}) {
            mvc.perform(sale(saleId()).with(req -> { req.setParameter("itemName", name); return req; }))
                .andExpect(model().attributeHasFieldErrors("form", "itemName"));
            mvc.perform(purchase(purchaseId()).with(req -> { req.setParameter("itemName", name); return req; }))
                .andExpect(model().attributeHasFieldErrors("purchaseForm", "itemName"));
            mvc.perform(saleFields(post("/transactions/" + id + "/edit")).param("version", "0")
                .with(req -> { req.setParameter("itemName", name); return req; }))
                .andExpect(model().attributeHasFieldErrors("form", "itemName"));
        }
        mvc.perform(purchase(purchaseId()).with(req -> { req.setParameter("itemName", "\u00a0商品 A\u202f"); return req; }))
            .andExpect(status().is3xxRedirection());
        assertEquals("商品 A", purchases.findAll().getFirst().getItemName());
        assertEquals("商品", sales.findById(id).getItemName());
        assertEquals(0, sales.findById(id).getVersion());
    }
}
