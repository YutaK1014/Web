package com.example.cashflow;

import com.example.cashflow.dto.SettingsForm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

class SettingsConflictTests extends SaveProtectionTests {
    @Autowired PlatformTransactionManager transactionManager;

    SettingsForm openSettings() throws Exception {
        return (SettingsForm) mvc.perform(get("/settings")).andExpect(status().isOk())
            .andExpect(content().string(containsString("name=\"version\"")))
            .andReturn().getModelAndView().getModel().get("settings");
    }

    MockHttpServletRequestBuilder saveSettings(String version, boolean alternate) {
        var request = post("/settings").param("mercari", alternate ? "20" : "10")
            .param("rakuma", alternate ? "8" : "6").param("yahoo", alternate ? "7" : "5")
            .param("rounding", alternate ? "UP" : "DOWN").param("monthlyGoal", alternate ? "50000" : "10000");
        return version == null ? request : request.param("version", version);
    }

    @Test void conflictPreservesAllInputAndFreshConfirmationAllowsSave() throws Exception {
        var original = openSettings();
        String version = original.getVersion().toString();
        mvc.perform(saveSettings(version, true)).andExpect(redirectedUrl("/settings"));
        var rejected = mvc.perform(saveSettings(version, false)).andExpect(status().isConflict())
            .andExpect(view().name("settings"))
            .andExpect(content().string(containsString("最新の設定画面を開く"))).andReturn();
        var retained = (SettingsForm) rejected.getModelAndView().getModel().get("settings");
        assertEquals(version, retained.getVersion().toString());
        assertEquals("10", retained.getMercari()); assertEquals("6", retained.getRakuma());
        assertEquals("5", retained.getYahoo()); assertEquals("DOWN", retained.getRounding());
        assertEquals("10000", retained.getMonthlyGoal());
        assertFalse(rejected.getFlashMap().containsKey("successMessage"));
        var saved = openSettings();
        assertEquals("20.00", saved.getMercari()); assertEquals("8.00", saved.getRakuma());
        assertEquals("7.00", saved.getYahoo()); assertEquals("UP", saved.getRounding());
        assertEquals("50000", saved.getMonthlyGoal());
        assertEquals(original.getVersion() + 1, saved.getVersion());
        mvc.perform(saveSettings(version, false)).andExpect(status().isConflict());
        mvc.perform(saveSettings(saved.getVersion().toString(), false)).andExpect(redirectedUrl("/settings"));
        assertEquals("10.00", openSettings().getMercari());
    }

    @Test void simultaneousInitialAndSubsequentSavesHaveOneWinnerWithoutMixedValues() throws Exception {
        jdbc.update("DELETE FROM marketplace_settings");
        jdbc.update("DELETE FROM app_settings");
        jdbc.update("UPDATE settings_version SET version=0 WHERE id=1");
        for (int round = 0; round < 2; round++) {
            String version = openSettings().getVersion().toString();
            assertSingleWinner(() -> mvc.perform(saveSettings(version, true)).andReturn().getResponse().getStatus(),
                () -> mvc.perform(saveSettings(version, false)).andReturn().getResponse().getStatus());
            var saved = openSettings();
            boolean alternate = saved.getMercari().equals("20.00");
            assertEquals(alternate ? "8.00" : "6.00", saved.getRakuma());
            assertEquals(alternate ? "7.00" : "5.00", saved.getYahoo());
            assertEquals(alternate ? "UP" : "DOWN", saved.getRounding());
            assertEquals(alternate ? "50000" : "10000", saved.getMonthlyGoal());
            assertEquals(round + 1L, saved.getVersion());
        }
    }

    @Test void missingMalformedAndFutureVersionsNeverSave() throws Exception {
        var original = openSettings();
        for (String version : new String[]{null, "", "-1", Long.toString(original.getVersion() + 1)})
            mvc.perform(saveSettings(version, true)).andExpect(status().isConflict());
        for (String version : new String[]{"abc", "0.1", "9223372036854775808"})
            mvc.perform(saveSettings(version, true)).andExpect(model().attributeHasFieldErrors("settings", "version"));
        assertEquals(original, openSettings());
    }

    @Test void validationAndRollbackDoNotConsumeVersionAndAllowRetry() throws Exception {
        var original = openSettings();
        String version = original.getVersion().toString();
        var invalid = mvc.perform(saveSettings(version, true).with(req -> { req.setParameter("mercari", "101"); return req; }))
            .andExpect(model().attributeHasFieldErrors("settings", "mercari")).andReturn();
        assertEquals(original.getVersion(), ((SettingsForm) invalid.getModelAndView().getModel().get("settings")).getVersion());
        var update = openSettings(); update.setMercari("20"); update.setMonthlyGoal("50000");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            marketplaces.save(update);
            status.setRollbackOnly();
        });
        assertEquals(original, openSettings());
        mvc.perform(saveSettings(version, true)).andExpect(redirectedUrl("/settings"));
    }

    @Test void schemaReinitializationKeepsVersionAndExistingSettings() throws Exception {
        var original = openSettings();
        mvc.perform(saveSettings(original.getVersion().toString(), true)).andExpect(redirectedUrl("/settings"));
        var saved = openSettings();
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
            new org.springframework.core.io.ClassPathResource("schema.sql")).execute(jdbc.getDataSource());
        assertEquals(saved, openSettings());
        mvc.perform(saveSettings(original.getVersion().toString(), false)).andExpect(status().isConflict());
    }
}
