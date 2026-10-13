package com.example.cashflow;

import com.example.cashflow.dto.PurchaseForm;
import com.example.cashflow.repository.PurchaseRepository;
import com.example.cashflow.repository.TagRepository;
import com.example.cashflow.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:purchases;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password="
})
class PurchaseHistoryTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    PurchaseRepository repository;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    TagRepository tags;
    @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;
    @Autowired ReportService reports;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        jdbc.update("DELETE FROM purchases");
    }

    @Test void homeShowsEmptyHistoryAndRegistrationForm() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk())
            .andExpect(content().string(containsString("購入履歴はまだありません")))
            .andExpect(content().string(containsString("購入履歴を登録する")))
            .andExpect(content().string(containsString("data-draft-key=\"purchase-new\"")));
    }

    @Test void draftCleanupRequiresSuccessfulRegistration() throws Exception {
        mvc.perform(post("/purchases").param("itemName", "下書きの商品")
            .param("purchasedDate", "2026-10-09").param("amount", "3000")
            .param("draftRevision", "purchase-revision"))
            .andExpect(flash().attribute("savedDraftKey", "purchase-new"))
            .andExpect(flash().attribute("savedDraftRevision", "purchase-revision"));
        var failed = mvc.perform(post("/purchases").param("itemName", "入力中")
            .param("draftRevision", "failed-revision"))
            .andExpect(view().name("home")).andReturn();
        assertFalse(failed.getFlashMap().containsKey("savedDraftKey"));
    }

    @Test void persistsAndDisplaysPurchasesInDateThenIdOrderWithoutChangingSales() throws Exception {
        var before = ReportService.summarize(reports.all());
        for (String date : new String[]{"2026-09-30", "2026-09-01", "2026-09-30"}) {
            mvc.perform(post("/purchases").param("itemName", " スニーカー ")
                .param("purchasedDate", date).param("amount", "3000")
                .param("store", " メルカリ ").param("memo", "<script>alert(1)</script>"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/#purchase-history"))
                .andExpect(flash().attribute("successMessage", "購入履歴を登録しました。"));
        }
        var rows = repository.findAll();
        assertEquals(3, rows.size());
        assertEquals("2026-09-01", rows.get(2).getPurchasedDate().toString());
        assertTrue(rows.get(0).getId() > rows.get(1).getId());
        assertEquals("スニーカー", rows.get(0).getItemName());
        assertEquals("メルカリ", rows.get(0).getStore());
        mvc.perform(get("/")).andExpect(status().isOk())
            .andExpect(content().string(containsString("スニーカー")))
            .andExpect(content().string(containsString("3,000円")))
            .andExpect(content().string(containsString("&lt;script&gt;")));
        assertEquals(before, ReportService.summarize(reports.all()));
    }

    @Test void invalidSubmissionRetainsInputAndExistingHistoryWithoutSaving() throws Exception {
        mvc.perform(post("/purchases").param("itemName", "登録済み商品")
            .param("purchasedDate", "2026-09-01").param("amount", "0"))
            .andExpect(status().is3xxRedirection());
        mvc.perform(post("/purchases").param("itemName", "入力中の商品")
            .param("purchasedDate", "2026-02-30").param("amount", "1000000001"))
            .andExpect(status().isOk()).andExpect(view().name("home"))
            .andExpect(model().attributeHasFieldErrors("purchaseForm", "purchasedDate", "amount"))
            .andExpect(content().string(containsString("入力中の商品")))
            .andExpect(content().string(containsString("登録済み商品")));
        assertEquals(1, repository.findAll().size());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder purchaseRequest() {
        return post("/purchases").param("itemName", "再送する商品")
            .param("purchasedDate", "2026-10-13").param("amount", "1234")
            .param("store", "購入先").param("memo", "<b>保持するメモ</b>")
            .param("tags", "衣類, ギフト").param("draftRevision", "retry-revision");
    }

    private void assertRetainedForm(org.springframework.test.web.servlet.MvcResult result) {
        var form = (PurchaseForm) result.getModelAndView().getModel().get("purchaseForm");
        assertEquals("再送する商品", form.getItemName());
        assertEquals("2026-10-13", form.getPurchasedDate());
        assertEquals("1234", form.getAmount());
        assertEquals("購入先", form.getStore());
        assertEquals("<b>保持するメモ</b>", form.getMemo());
        assertEquals("衣類, ギフト", form.getTags());
        assertFalse(result.getFlashMap().containsKey("savedDraftKey"));
        assertFalse(result.getFlashMap().containsKey("savedDraftRevision"));
        assertFalse(result.getFlashMap().containsKey("successMessage"));
    }

    @Test void databaseWriteFailureKeepsFormAndHistoryAndAllowsRetryWithoutDuplicates() throws Exception {
        mvc.perform(post("/purchases").param("itemName", "登録済み商品")
            .param("purchasedDate", "2026-09-01").param("amount", "0"))
            .andExpect(status().is3xxRedirection());
        var delegate = sqlSession.getMapper(TagRepository.class);
        // Fail after the purchase INSERT to also check transaction rollback.
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("test write failure"))
            .doAnswer(invocation -> {
                delegate.addPurchase(invocation.getArgument(0), invocation.getArgument(1));
                return null;
            }).when(tags).addPurchase(anyLong(), anyString());
        var failed = mvc.perform(purchaseRequest())
            .andExpect(status().isServiceUnavailable()).andExpect(view().name("home"))
            .andExpect(model().attributeHasErrors("purchaseForm"))
            .andExpect(content().string(containsString("保存できませんでした")))
            .andExpect(content().string(containsString("登録済み商品")))
            .andExpect(content().string(containsString("value=\"再送する商品\"")))
            .andExpect(content().string(containsString("value=\"2026-10-13\"")))
            .andExpect(content().string(containsString("value=\"1234\"")))
            .andExpect(content().string(containsString("value=\"購入先\"")))
            .andExpect(content().string(containsString("&lt;b&gt;保持するメモ&lt;/b&gt;")))
            .andExpect(content().string(containsString("value=\"衣類, ギフト\"")))
            .andReturn();
        assertRetainedForm(failed);
        assertEquals(1, repository.findAll().size());
        mvc.perform(purchaseRequest()).andExpect(redirectedUrl("/#purchase-history"))
            .andExpect(flash().attribute("savedDraftKey", "purchase-new"))
            .andExpect(flash().attribute("savedDraftRevision", "retry-revision"));
        assertEquals(2, repository.findAll().size());
        assertEquals(2, tags.purchaseTags(repository.findAll().getFirst().getId()).size());
    }

    @Test void databaseWriteAndReadFailureStillRendersFormWithoutClaimingHistoryIsEmpty() throws Exception {
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("test write failure"))
            .when(tags).addPurchase(anyLong(), anyString());
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("test read failure"))
            .when(repository).search(anyString(), any(), any());
        var failed = mvc.perform(purchaseRequest())
            .andExpect(status().isServiceUnavailable()).andExpect(view().name("home"))
            .andExpect(model().attribute("purchaseHistoryUnavailable", true))
            .andExpect(content().string(containsString("購入履歴を読み込めませんでした")))
            .andExpect(content().string(containsString("再送する商品")))
            .andExpect(content().string(containsString("&lt;b&gt;保持するメモ&lt;/b&gt;")))
            .andExpect(content().string(not(containsString("購入履歴はまだありません"))))
            .andExpect(content().string(not(containsString("該当する購入履歴はありません"))))
            .andReturn();
        assertRetainedForm(failed);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM purchases", Integer.class));
    }

    @Test void validationErrorsSurviveHistoryReadFailure() throws Exception {
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("test read failure"))
            .when(repository).search(anyString(), any(), any());
        mvc.perform(post("/purchases").param("itemName", "入力中の商品")
            .param("purchasedDate", "2026-02-30").param("amount", "-1"))
            .andExpect(status().isServiceUnavailable()).andExpect(view().name("home"))
            .andExpect(model().attributeHasFieldErrors("purchaseForm", "purchasedDate", "amount"))
            .andExpect(content().string(containsString("入力中の商品")))
            .andExpect(content().string(containsString("購入履歴を読み込めませんでした")));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM purchases", Integer.class));
    }

    @Test void enforcesRequiredFieldsLengthsAndAmountFormat() {
        for (String amount : new String[]{"", "-1", "1.5", "1,000", "99999999999", "abc"}) {
            PurchaseForm form = new PurchaseForm();
            form.setItemName(" "); form.setAmount(amount); form.setPurchasedDate("0999-12-31");
            form.setStore("a".repeat(101)); form.setMemo("a".repeat(10001));
            var errors = new BeanPropertyBindingResult(form, "purchaseForm");
            form.validate(errors);
            for (String field : new String[]{"itemName", "amount", "purchasedDate", "store", "memo"})
                assertTrue(errors.hasFieldErrors(field), field);
        }
        PurchaseForm form = new PurchaseForm();
        form.setItemName("a".repeat(100)); form.setAmount("1000000000");
        form.setPurchasedDate("2024-02-29"); form.setStore("a".repeat(100)); form.setMemo("a".repeat(10000));
        var errors = new BeanPropertyBindingResult(form, "purchaseForm");
        form.validate(errors);
        assertFalse(errors.hasErrors());
    }
}
