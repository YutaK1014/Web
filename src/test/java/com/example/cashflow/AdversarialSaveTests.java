package com.example.cashflow;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exploratory characterization: passing assertions also document remaining weaknesses.
 * Inherits the seven protection tests and their isolated H2 setup.
 */
class AdversarialSaveTests extends SaveProtectionTests {
    long createSale() throws Exception {
        mvc.perform(sale(saleId())).andExpect(status().is3xxRedirection());
        return sales.search("", "", null, null).getFirst().getId();
    }

    @Test void replayWithChangedContentCannotChangeOriginalOrAddRecord() throws Exception {
        String token = saleId();
        mvc.perform(sale(token)).andExpect(status().is3xxRedirection());
        mvc.perform(sale(token).with(req -> {
            req.setParameter("sellingPrice", "99999"); req.setParameter("tags", "changed"); return req;
        })).andExpect(status().isConflict());
        var rows = sales.search("", "", null, null);
        assertEquals(1, rows.size());
        assertEquals(3000, rows.getFirst().getSellingPrice());
        assertEquals(java.util.List.of("元のタグ"), tags.transactionTags(rows.getFirst().getId()));
    }

    @Test void delayedReplayAfterDeletionCannotRecreateRecord() throws Exception {
        String token = saleId();
        mvc.perform(sale(token)).andExpect(status().is3xxRedirection());
        long id = sales.search("", "", null, null).getFirst().getId();
        mvc.perform(post("/transactions/" + id + "/delete").param("version", "0")).andExpect(status().is3xxRedirection());
        mvc.perform(sale(token)).andExpect(status().isConflict());
        assertTrue(sales.search("", "", null, null).isEmpty());
    }

    @Test void saleSubmissionCannotBeReusedForPurchase() throws Exception {
        String token = saleId();
        mvc.perform(sale(token)).andExpect(status().is3xxRedirection());
        mvc.perform(purchase(token)).andExpect(status().isConflict());
        assertTrue(purchases.findAll().isEmpty());
    }

    @Test void malformedNegativeAndFutureVersionsDoNotUpdate() throws Exception {
        long id = createSale();
        for (String version : new String[]{"", "-1", "1", "abc", "9223372036854775808", "0.0"}) {
            mvc.perform(saleFields(post("/transactions/" + id + "/edit")).param("version", version)
                .with(req -> { req.setParameter("sellingPrice", "9000"); return req; }))
                .andExpect(model().hasErrors());
            assertEquals(3000, sales.findById(id).getSellingPrice());
            assertEquals(0, sales.findById(id).getVersion());
        }
    }

    @Test void staleEditCannotApplyPhotoRemovalOrInvalidImage() throws Exception {
        long id = createSale();
        jdbc.update("UPDATE transactions SET image_path='/images/existing.png' WHERE id=?", id);
        String url = "/transactions/" + id + "/edit";
        mvc.perform(saleFields(post(url)).param("version", "0")).andExpect(status().is3xxRedirection());
        mvc.perform(saleFields(multipart(url).file(new org.springframework.mock.web.MockMultipartFile(
            "photo", "broken.png", "image/png", new byte[]{1}))).param("version", "0").param("removePhoto", "true"))
            .andExpect(status().isConflict());
        assertEquals("/images/existing.png", sales.findById(id).getImagePath());
        assertEquals(1, sales.findById(id).getVersion());
    }

    @Test void staleReturnFormStillOverwritesNewerReturnCost() throws Exception {
        long id = createSale();
        String url = "/transactions/" + id + "/return";
        mvc.perform(get(url)).andExpect(status().isOk()); // tab A
        mvc.perform(get(url)).andExpect(status().isOk()); // tab B
        mvc.perform(post(url).param("returnCost", "1200")).andExpect(status().is3xxRedirection());
        mvc.perform(post(url).param("returnCost", "100")).andExpect(status().is3xxRedirection());
        assertEquals(100, sales.findById(id).getReturnCost());
        assertEquals(-100, sales.findById(id).getProfit());
    }

    @Test void staleDeleteConfirmationCannotDeleteNewerEditedRecord() throws Exception {
        long id = createSale();
        mvc.perform(get("/transactions/" + id + "/delete")).andExpect(status().isOk());
        mvc.perform(saleFields(post("/transactions/" + id + "/edit")).param("version", "0")
            .with(req -> { req.setParameter("sellingPrice", "5000"); return req; }))
            .andExpect(status().is3xxRedirection());
        mvc.perform(post("/transactions/" + id + "/delete").param("version", "0")).andExpect(status().isConflict());
        assertEquals(5000, sales.findById(id).getSellingPrice());
    }

    @Test void deleteConflictRequiresFreshConfirmationAndPreservesTags() throws Exception {
        long id = createSale();
        String url = "/transactions/" + id + "/delete";
        mvc.perform(get(url)).andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"version\" value=\"0\"")));
        mvc.perform(saleFields(post("/transactions/" + id + "/edit")).param("version", "0")
            .with(req -> { req.setParameter("tags", "最新タグ"); return req; })).andExpect(status().is3xxRedirection());
        for (int attempt = 0; attempt < 2; attempt++) {
            var failed = mvc.perform(post(url).param("version", "0")).andExpect(status().isConflict())
                .andExpect(view().name("transaction-delete"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("最新の削除確認画面を開く")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("type=\"submit\""))))
                .andReturn();
            assertFalse(failed.getFlashMap().containsKey("successMessage"));
            assertEquals(java.util.List.of("最新タグ"), tags.transactionTags(id));
        }
        var page = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        var row = (com.example.cashflow.entity.Transaction) page.getModelAndView().getModel().get("transaction");
        mvc.perform(post(url).param("version", Long.toString(row.getVersion())))
            .andExpect(redirectedUrl("/transactions"));
        assertNull(sales.findById(id));
        assertTrue(tags.transactionTags(id).isEmpty());
        mvc.perform(post(url).param("version", Long.toString(row.getVersion()))).andExpect(status().isNotFound());
    }

    @Test void missingInvalidAndFutureDeleteVersionsCannotDelete() throws Exception {
        long id = createSale();
        String url = "/transactions/" + id + "/delete";
        mvc.perform(post(url)).andExpect(status().isConflict());
        for (String version : new String[]{"", "-1", "1"})
            mvc.perform(post(url).param("version", version)).andExpect(status().isConflict());
        for (String version : new String[]{"abc", "0.5", "9223372036854775808"})
            mvc.perform(post(url).param("version", version)).andExpect(status().isBadRequest());
        assertNotNull(sales.findById(id));
    }

    @Test void returnAfterDeleteConfirmationAlsoBlocksDeletion() throws Exception {
        long id = createSale();
        mvc.perform(get("/transactions/" + id + "/delete")).andExpect(status().isOk());
        mvc.perform(post("/transactions/" + id + "/return").param("returnCost", "1200"))
            .andExpect(status().is3xxRedirection());
        mvc.perform(post("/transactions/" + id + "/delete").param("version", "0"))
            .andExpect(status().isConflict());
        assertEquals(1200, sales.findById(id).getReturnCost());
    }

    @Test void invisibleFormatCharactersStillRegisterAsNames() throws Exception {
        for (String name : new String[]{"\u200b", "\ufeff", "\u200d"}) {
            mvc.perform(sale(saleId()).with(req -> { req.setParameter("itemName", name); return req; }))
                .andExpect(status().is3xxRedirection());
            mvc.perform(purchase(purchaseId()).with(req -> { req.setParameter("itemName", name); return req; }))
                .andExpect(status().is3xxRedirection());
        }
        assertEquals(3, sales.search("", "", null, null).size());
        assertEquals(3, purchases.findAll().size());
    }

    @Test void changingSubmissionIdAllowsIdenticalRecordAsSeparateSubmission() throws Exception {
        // Form IDs prevent accidental replay; they are not authentication or content deduplication.
        mvc.perform(sale(saleId())).andExpect(status().is3xxRedirection());
        mvc.perform(sale(UUID.randomUUID().toString())).andExpect(status().is3xxRedirection());
        assertEquals(2, sales.search("", "", null, null).size());
    }
}
