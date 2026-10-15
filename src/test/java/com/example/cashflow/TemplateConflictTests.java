package com.example.cashflow;

import com.example.cashflow.dto.ShippingTemplateForm;
import com.example.cashflow.repository.ShippingTemplateRepository;
import com.example.cashflow.service.ShippingTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

class TemplateConflictTests extends SaveProtectionTests {
    @Autowired ShippingTemplateRepository templates;
    @Autowired ShippingTemplateService templateService;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    @BeforeEach void clearTemplates() { jdbc.update("DELETE FROM shipping_templates"); }

    MockHttpServletRequestBuilder edit(long id, String version, String name, String cost) {
        var request = post("/settings/shipping-templates/" + id + "/edit")
            .param("name", name).param("shippingMethod", "宅配便").param("packaging", "箱")
            .param("shippingCost", cost).param("packagingCost", "50");
        return version == null ? request : request.param("version", version);
    }

    long create() throws Exception {
        mvc.perform(post("/settings/shipping-templates").param("name", "元の名前").param("shippingMethod", "封筒便")
            .param("packaging", "封筒").param("shippingCost", "230").param("packagingCost", "30"))
            .andExpect(status().is3xxRedirection());
        return templates.findAll().getFirst().getId();
    }

    @Test void conflictKeepsInputAndFreshEditCanSave() throws Exception {
        long id = create();
        String url = "/settings/shipping-templates/" + id + "/edit";
        var original = (ShippingTemplateForm) mvc.perform(get(url)).andReturn().getModelAndView().getModel().get("templateForm");
        assertEquals(0L, original.getVersion());
        mvc.perform(edit(id, "0", "更新済み", "750")).andExpect(status().is3xxRedirection());
        var rejected = mvc.perform(edit(id, "0", "古いタブの入力", "300")).andExpect(status().isConflict())
            .andExpect(content().string(containsString("最新の編集画面を開く"))).andReturn();
        var retained = (ShippingTemplateForm) rejected.getModelAndView().getModel().get("templateForm");
        assertEquals("古いタブの入力", retained.getName()); assertEquals("300", retained.getShippingCost());
        assertEquals("宅配便", retained.getShippingMethod()); assertEquals("箱", retained.getPackaging());
        assertEquals("50", retained.getPackagingCost()); assertEquals(0L, retained.getVersion());
        assertFalse(rejected.getFlashMap().containsKey("successMessage"));
        assertEquals("更新済み", templates.findById(id).getName());
        var refreshed = (ShippingTemplateForm) mvc.perform(get(url)).andReturn().getModelAndView().getModel().get("templateForm");
        assertEquals(1L, refreshed.getVersion());
        mvc.perform(edit(id, refreshed.getVersion().toString(), "確認後", "300")).andExpect(status().is3xxRedirection());
        assertEquals(2, templates.findById(id).getVersion());
    }

    @Test void deleteConflictRequiresReconfirmation() throws Exception {
        long id = create();
        String url = "/settings/shipping-templates/" + id + "/delete";
        mvc.perform(get(url)).andExpect(content().string(containsString("name=\"version\" value=\"0\"")));
        mvc.perform(edit(id, "0", "更新済み", "750")).andExpect(status().is3xxRedirection());
        for (int attempt = 0; attempt < 2; attempt++) {
            var rejected = mvc.perform(post(url).param("version", "0")).andExpect(status().isConflict())
                .andExpect(content().string(containsString("最新の削除確認画面を開く")))
                .andExpect(content().string(not(containsString("type=\"submit\"")))).andReturn();
            assertFalse(rejected.getFlashMap().containsKey("successMessage"));
        }
        assertEquals(800, templates.findById(id).getTotalCost());
        mvc.perform(get(url)).andExpect(content().string(containsString("name=\"version\" value=\"1\"")));
        mvc.perform(post(url).param("version", "1")).andExpect(status().is3xxRedirection());
        assertNull(templates.findById(id));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM shipping_template_versions", Integer.class));
        mvc.perform(post(url).param("version", "1")).andExpect(status().isNotFound());
    }

    @Test void simultaneousEditsHaveOnlyOneWinner() throws Exception {
        long id = create();
        assertSingleWinner(() -> mvc.perform(edit(id, "0", "A", "750")).andReturn().getResponse().getStatus(),
            () -> mvc.perform(edit(id, "0", "B", "300")).andReturn().getResponse().getStatus());
        var row = templates.findById(id);
        assertEquals(1, row.getVersion());
        assertEquals(row.getName().equals("A") ? 750 : 300, row.getShippingCost());
    }

    @Test void updateAndDeleteSerializeAndDoNotDeleteNewerVersion() throws Exception {
        long id = create();
        String url = "/settings/shipping-templates/" + id + "/delete";
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var update = executor.submit(() -> { start.await(); return mvc.perform(edit(id, "0", "更新済み", "750")).andReturn().getResponse().getStatus(); });
            var delete = executor.submit(() -> { start.await(); return mvc.perform(post(url).param("version", "0")).andReturn().getResponse().getStatus(); });
            start.countDown();
            int updated = update.get(15, java.util.concurrent.TimeUnit.SECONDS);
            int deleted = delete.get(15, java.util.concurrent.TimeUnit.SECONDS);
            if (updated == 302) {
                assertEquals(409, deleted);
                assertEquals("更新済み", templates.findById(id).getName());
            } else {
                assertEquals(404, updated); assertEquals(302, deleted);
                assertNull(templates.findById(id));
            }
        }
    }

    @Test void missingAndInvalidVersionsCannotBypassProtection() throws Exception {
        long id = create();
        String url = "/settings/shipping-templates/" + id + "/delete";
        mvc.perform(post(url)).andExpect(status().isConflict());
        for (String version : new String[]{null, "", "-1", "1"})
            mvc.perform(edit(id, version, "不正", "500")).andExpect(status().isConflict());
        for (String version : new String[]{"", "-1", "1"})
            mvc.perform(post(url).param("version", version)).andExpect(status().isConflict());
        for (String version : new String[]{"abc", "0.1", "9223372036854775808"}) {
            mvc.perform(edit(id, version, "不正", "500")).andExpect(model().attributeHasFieldErrors("templateForm", "version"));
            mvc.perform(post(url).param("version", version)).andExpect(status().isBadRequest());
        }
        assertEquals("元の名前", templates.findById(id).getName());
        assertEquals(0, templates.findById(id).getVersion());
    }

    @Test void validationAndRollbackKeepVersionAndSavedFields() throws Exception {
        long id = create();
        var invalid = mvc.perform(edit(id, "0", "入力保持", "-1"))
            .andExpect(model().attributeHasFieldErrors("templateForm", "shippingCost")).andReturn();
        assertEquals(0L, ((ShippingTemplateForm) invalid.getModelAndView().getModel().get("templateForm")).getVersion());
        var form = ShippingTemplateForm.from(templates.findById(id));
        form.setName("ロールバック"); form.setShippingCost("750");
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            templateService.save(id, form); status.setRollbackOnly();
        });
        assertEquals("元の名前", templates.findById(id).getName());
        assertEquals(0, templates.findById(id).getVersion());
        mvc.perform(edit(id, "0", "訂正後", "750")).andExpect(status().is3xxRedirection());
    }
}
