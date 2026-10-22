package com.example.cashflow;

import com.example.cashflow.repository.TransactionRepository;
import com.example.cashflow.service.TransactionService;
import com.example.cashflow.service.MarketplaceService;
import com.example.cashflow.dto.SettingsForm;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:transactions;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "app.photos.directory=./target/test-photos"
})
class TransactionRegistrationTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    TransactionRepository repository;
    @Autowired MarketplaceService marketplaces;
    @Autowired TransactionService service;
    @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = CsrfTestSupport.mvc(context);
        jdbc.update("DELETE FROM transactions");
        jdbc.update("DELETE FROM marketplace_settings");
        jdbc.update("DELETE FROM app_settings");
        SettingsForm settings = new SettingsForm();
        settings.setMercari("10"); settings.setRakuma("6"); settings.setYahoo("5"); settings.setRounding("DOWN");
        settings.setVersion(marketplaces.settings().getVersion()); marketplaces.save(settings);
    }

    @Test void registrationFormOpensWithoutLoginAndIssuesCsrfToken() throws Exception {
        var result = MockMvcBuilders.webAppContextSetup(context).build()
            .perform(get("/sales/register")).andExpect(status().isOk())
            .andExpect(content().string(containsString("Yahoo!フリマ")))
            .andExpect(content().string(containsString("販売日"))).andReturn();
        assertFalse(CsrfTestSupport.token(result.getResponse().getContentAsString()).isBlank());
        assertNotNull(result.getRequest().getSession(false));
        assertTrue(result.getResponse().getContentAsString().contains("data-rate=\"10.00\""),
            "The browser needs the configured marketplace rate for its live preview");
    }

    MockMultipartHttpServletRequestBuilder valid(String url) {
        var request = multipart(url).param("submissionId", java.util.UUID.randomUUID().toString());
        if (url.endsWith("/edit")) request.param("version", Long.toString(repository.findById(Long.parseLong(url.split("/")[2])).getVersion()));
        request.param("itemName", "商品A").param("sellingPrice","3000").param("shippingCost","750")
            .param("purchasePrice","1000").param("marketplace","メルカリ").param("feeRate","99")
            .param("soldDate","2026-01-02").param("memo","テストメモ");
        return request;
    }

    MockMultipartFile photo() throws Exception {
        var buffer = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",buffer);
        return new MockMultipartFile("photo","../../photo.png","image/png",buffer.toByteArray());
    }

    long create() throws Exception {
        mvc.perform(valid("/transactions")).andExpect(redirectedUrl("/transactions"));
        return repository.search("","",null,null).getFirst().getId();
    }

    @Test void draftCleanupRequiresSuccessfulSaveAndSeparatesEditKey() throws Exception {
        mvc.perform(get("/sales/register"))
            .andExpect(content().string(containsString("data-draft-key=\"sale-new\"")));
        mvc.perform(valid("/transactions").param("draftRevision", "revision-new"))
            .andExpect(flash().attribute("savedDraftKey", "sale-new"))
            .andExpect(flash().attribute("savedDraftRevision", "revision-new"));
        mvc.perform(get("/transactions"))
            .andExpect(content().string(containsString("data-saved-draft=\"sale-new\"")))
            .andExpect(content().string(containsString("data-saved-revision=\"revision-new\"")));
        long id = repository.search("", "", null, null).getFirst().getId();
        mvc.perform(get("/transactions/" + id + "/edit"))
            .andExpect(content().string(containsString("data-draft-key=\"sale-" + id + "\"")));
        mvc.perform(valid("/transactions/" + id + "/edit").param("draftRevision", "revision-edit"))
            .andExpect(flash().attribute("savedDraftKey", "sale-" + id))
            .andExpect(flash().attribute("savedDraftRevision", "revision-edit"));
        mvc.perform(get("/transactions"))
            .andExpect(content().string(containsString("data-saved-draft=\"sale-" + id + "\"")))
            .andExpect(content().string(containsString("data-saved-revision=\"revision-edit\"")));
        var failed = mvc.perform(multipart("/transactions").param("submissionId", java.util.UUID.randomUUID().toString()).param("itemName", "入力中")
            .param("draftRevision", "revision-failed"))
            .andExpect(view().name("transaction-form")).andReturn();
        assertFalse(failed.getFlashMap().containsKey("savedDraftKey"));
    }

    @Test void savesServerCalculatedFeesDatesAndMemoAndRendersList() throws Exception {
        long id = create();
        var row = repository.findById(id);
        assertEquals(300,row.getSellingFee()); assertEquals(950,row.getProfit());
        assertEquals("2026-01-02",row.getSoldDate().toString()); assertEquals("テストメモ",row.getMemo());
        assertEquals(950L,jdbc.queryForObject("SELECT profit FROM transactions WHERE id=?",Long.class,id));
        mvc.perform(get("/transactions")).andExpect(status().isOk())
            .andExpect(content().string(containsString("950円")))
            .andExpect(content().string(containsString("2026-01-02")))
            .andExpect(content().string(containsString("/transactions/"+id+"/edit")));
    }

    @Test void collapsedFiltersRetainSearchAndTotalsAndOpenOnErrors() throws Exception {
        create();
        String initial = mvc.perform(get("/transactions")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertTrue(initial.contains("id=\"transaction-filters\">"));
        assertFalse(initial.contains("（条件指定あり）"));
        assertTrue(initial.contains("id=\"transaction-total-details\"><summary>経費・件数の詳細</summary>"));

        String filtered = mvc.perform(get("/transactions").param("keyword", "商品A"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(filtered.contains("id=\"transaction-filters\">"));
        assertTrue(filtered.contains("（条件指定あり）"));
        assertTrue(filtered.contains("value=\"商品A\""));
        String totals = filtered.substring(filtered.indexOf("class=\"transaction-totals\""),
            filtered.indexOf("id=\"transaction-total-details\""));
        assertTrue(totals.contains("3,000円"));
        assertTrue(totals.contains("950円"));
        mvc.perform(get("/transactions/export").param("keyword", "商品A"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("商品A")));
        mvc.perform(get("/transactions").param("keyword", "該当なし"))
            .andExpect(content().string(containsString("該当する取引はありません")));

        String invalid = mvc.perform(get("/transactions").param("period", "custom")
            .param("from", "2026-02-10").param("to", "2026-02-01"))
            .andExpect(model().hasErrors()).andReturn().getResponse().getContentAsString();
        assertTrue(java.util.regex.Pattern.compile("<details[^>]*id=\"transaction-filters\"[^>]*open[^>]*>")
            .matcher(invalid).find(), "Invalid search conditions must be visible for correction");
        assertTrue(invalid.contains("value=\"2026-02-10\""));
    }

    @Test void editRecalculatesAndDeleteConfirmationDoesNotDeleteUntilPost() throws Exception {
        long id = create();
        mvc.perform(get("/transactions/"+id+"/edit")).andExpect(status().isOk()).andExpect(content().string(containsString("テストメモ")));
        var request = valid("/transactions/"+id+"/edit");
        request.param("sellingPrice","4000");
        // Replace, rather than append, the parameter.
        request.with(req -> { req.setParameter("sellingPrice","4000"); return req; });
        mvc.perform(request).andExpect(redirectedUrl("/transactions"));
        assertEquals(1850,repository.findById(id).getProfit());
        mvc.perform(get("/transactions/"+id+"/delete")).andExpect(status().isOk()).andExpect(content().string(containsString("削除確認")));
        assertNotNull(repository.findById(id));
        mvc.perform(post("/transactions/"+id+"/delete").param("version", "1"))
            .andExpect(redirectedUrl("/transactions"));
        assertNull(repository.findById(id));
        mvc.perform(get("/transactions/"+id+"/edit")).andExpect(status().isNotFound());
    }

    @Test void validatesOtherSiteAndMissingDatesAndPreservesInput() throws Exception {
        mvc.perform(multipart("/transactions").param("submissionId", java.util.UUID.randomUUID().toString())
            .param("itemName","入力を保持").param("sellingPrice","1000").param("shippingCost","0").param("marketplace","その他"))
            .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form","soldDate","customMarketplace","feeRate"))
            .andExpect(content().string(containsString("入力を保持")));
        assertTrue(repository.search("","",null,null).isEmpty());
        mvc.perform(multipart("/transactions").param("submissionId", java.util.UUID.randomUUID().toString())
            .param("itemName","その他取引").param("sellingPrice","1000").param("shippingCost","100")
            .param("marketplace","その他").param("customMarketplace","地域フリマ").param("feeRate","5.25").param("soldDate","2026-03-01"))
            .andExpect(redirectedUrl("/transactions"));
        assertEquals(848,repository.search("","",null,null).getFirst().getProfit());
    }

    @Test void newImageReplacementAndDeletionRemoveFiles() throws Exception {
        mvc.perform(valid("/transactions").file(photo())).andExpect(redirectedUrl("/transactions"));
        var row = repository.search("","",null,null).getFirst();
        Path old = Path.of("target/test-photos",row.getImagePath().substring("/images/".length()));
        assertTrue(Files.exists(old));
        mvc.perform(valid("/transactions/"+row.getId()+"/edit").file(photo())).andExpect(redirectedUrl("/transactions"));
        assertFalse(Files.exists(old));
        Path replacement = Path.of("target/test-photos",repository.findById(row.getId()).getImagePath().substring("/images/".length()));
        assertTrue(Files.exists(replacement));
        service.delete(row.getId(), 1L); assertFalse(Files.exists(replacement));
    }

    @Test void concurrentPhotoReplacementRejectsStaleDeletionAndKeepsNewFile() throws Exception {
        mvc.perform(valid("/transactions").file(photo())).andExpect(redirectedUrl("/transactions"));
        var row = repository.search("", "", null, null).getFirst();
        Path oldPhoto = Path.of("target/test-photos", row.getImagePath().substring("/images/".length()));
        var form = com.example.cashflow.dto.TransactionForm.from(row);
        form.setPhoto(photo());
        var deletionReachedDatabase = new java.util.concurrent.CountDownLatch(1);
        var deleting = new java.util.concurrent.atomic.AtomicBoolean();
        var delegate = sqlSession.getMapper(TransactionRepository.class);
        // With locking, deletion reaches lockById first. Without it, capture the
        // stale read before committing the replacement to reproduce the leak.
        org.mockito.Mockito.doAnswer(invocation -> {
            if (deleting.get()) deletionReachedDatabase.countDown();
            return delegate.lockById(row.getId());
        }).when(repository).lockById(row.getId());
        org.mockito.Mockito.doAnswer(invocation -> {
            Object result = delegate.findById(row.getId());
            if (deleting.get()) deletionReachedDatabase.countDown();
            return result;
        }).when(repository).findById(row.getId());
        Path[] replacement = new Path[1];
        var deletion = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<?>>();
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                try { service.save(row.getId(), form); }
                catch (java.io.IOException exception) { throw new RuntimeException(exception); }
                replacement[0] = Path.of("target/test-photos",
                    repository.findById(row.getId()).getImagePath().substring("/images/".length()));
                assertTrue(Files.exists(replacement[0]));
                deleting.set(true);
                deletion.set(executor.submit(() -> service.delete(row.getId(), 0L)));
                try { assertTrue(deletionReachedDatabase.await(5, java.util.concurrent.TimeUnit.SECONDS)); }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(exception);
                }
            });
            var conflict = assertThrows(java.util.concurrent.ExecutionException.class,
                () -> deletion.get().get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertInstanceOf(com.example.cashflow.service.SaveConflictException.class, conflict.getCause());
        }
        assertNotNull(repository.findById(row.getId()));
        assertFalse(Files.exists(oldPhoto), "The replaced photo should be removed after the update commits");
        assertTrue(Files.exists(replacement[0]), "Rejected deletion must preserve the updated photo");
        service.delete(row.getId(), 1L);
        assertFalse(Files.exists(replacement[0]));
    }

    @Test void supportsLiteralKeywordDateAndPlatformFiltersAndCsvEscaping() throws Exception {
        long id = create();
        jdbc.update("UPDATE transactions SET item_name=?, memo=? WHERE id=?", "=SUM(1,2)%", "a,\"b\"\nline", id);
        mvc.perform(get("/transactions").param("keyword","%").param("month","2026-01"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("=SUM(1,2)%")));
        mvc.perform(get("/transactions").param("month","2026-02"))
            .andExpect(content().string(containsString("該当する取引はありません")));
        mvc.perform(get("/transactions").param("marketplace","ラクマ"))
            .andExpect(content().string(containsString("該当する取引はありません")));
        mvc.perform(get("/transactions").param("from","invalid"))
            .andExpect(status().isOk()).andExpect(model().hasErrors());
        mvc.perform(get("/transactions/export").param("keyword","%"))
            .andExpect(status().isOk()).andExpect(header().string("Content-Disposition","attachment; filename=transactions.csv"))
            .andExpect(content().string(containsString("\"'=SUM(1,2)%\"")))
            .andExpect(content().string(containsString("\"a,\"\"b\"\"\nline\"")));
        mvc.perform(get("/transactions/export").param("month","invalid")).andExpect(status().isBadRequest());
    }

    @Test void rejectsOutOfRangeSearchDatesBeforeListingOrExporting() throws Exception {
        for (String field : List.of("month", "from", "to")) {
            for (String year : List.of("0999", "+10000", "-0001")) {
                String value = year + (field.equals("month") ? "-01" : "-01-01");
                mvc.perform(get("/transactions").param(field, value))
                    .andExpect(status().isOk()).andExpect(model().hasErrors())
                    .andExpect(content().string(containsString("販売月・販売期間を正しく入力してください。")));
                mvc.perform(get("/transactions/export").param(field, value))
                    .andExpect(status().isBadRequest());
            }
        }
        // An invalid boundary must not be hidden by intersecting it with a valid month.
        mvc.perform(get("/transactions").param("month", "2026-01").param("from", "0999-01-01"))
            .andExpect(status().isOk()).andExpect(model().hasErrors());
        mvc.perform(get("/transactions").param("from", "1000-01-01").param("to", "9999-12-31"))
            .andExpect(status().isOk()).andExpect(model().hasNoErrors());
    }

    @Test void rendersAllPagesAndKeepsExistingAmountsWhenSettingsChange() throws Exception {
        long id = create();
        for (String url : List.of("/","/dashboard","/reports?year=2026","/settings","/transactions")) {
            mvc.perform(get(url)).andExpect(status().isOk());
        }
        mvc.perform(post("/settings").param("version", context.getBean(com.example.cashflow.service.MarketplaceService.class).settings().getVersion().toString())
            .param("mercari","20").param("rakuma","6").param("yahoo","5").param("rounding","HALF_UP").param("monthlyGoal","10000"))
            .andExpect(redirectedUrl("/settings"));
        assertEquals(300,repository.findById(id).getSellingFee());
        assertEquals(10000L,marketplaces.monthlyGoal());
        mvc.perform(valid("/transactions/"+id+"/edit")).andExpect(redirectedUrl("/transactions"));
        assertEquals(600,repository.findById(id).getSellingFee());
        mvc.perform(get("/reports").param("year","2026")).andExpect(content().string(containsString("650円")));
    }

    @Test void validatesWritesWithoutTokenAndRequiresConfiguredRates() throws Exception {
        mvc.perform(post("/settings").param("version", context.getBean(com.example.cashflow.service.MarketplaceService.class).settings().getVersion().toString())).andExpect(status().isOk()).andExpect(model().hasErrors());
        mvc.perform(multipart("/transactions").param("submissionId", java.util.UUID.randomUUID().toString())).andExpect(status().isOk()).andExpect(model().hasErrors());
        mvc.perform(post("/transactions/0/edit")).andExpect(status().isNotFound());
        jdbc.update("DELETE FROM marketplace_settings");
        mvc.perform(valid("/transactions")).andExpect(status().isOk())
            .andExpect(content().string(containsString("設定画面でフリマサイトの手数料率を設定してください")));
        assertTrue(repository.search("","",null,null).isEmpty());
    }

    @Test void rollsBackDatabaseAndNewPhotoTogether() throws Exception {
        var form = new com.example.cashflow.dto.TransactionForm();
        form.setSubmissionId(java.util.UUID.randomUUID().toString()); form.setItemName("ロールバック確認"); form.setSellingPrice("1000"); form.setShippingCost("100");
        form.setMarketplace("メルカリ"); form.setSoldDate("2026-01-01"); form.setPhoto(photo());
        Path[] savedPhoto = new Path[1];
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            try { service.save(null,form); } catch (java.io.IOException exception) { throw new RuntimeException(exception); }
            var row = repository.search("","",null,null).getFirst();
            savedPhoto[0] = Path.of("target/test-photos",row.getImagePath().substring("/images/".length()));
            assertTrue(Files.exists(savedPhoto[0]));
            status.setRollbackOnly();
        });
        assertTrue(repository.search("","",null,null).isEmpty());
        assertFalse(Files.exists(savedPhoto[0]));
    }

    @Test void deletesOnlyPhotoAndEscapesUserText() throws Exception {
        mvc.perform(valid("/transactions").file(photo())).andExpect(redirectedUrl("/transactions"));
        var row = repository.search("","",null,null).getFirst();
        Path savedPhoto = Path.of("target/test-photos",row.getImagePath().substring("/images/".length()));
        var request = valid("/transactions/"+row.getId()+"/edit");
        request.param("removePhoto","true");
        request.with(req -> { req.setParameter("itemName","<script>alert(1)</script>"); return req; });
        mvc.perform(request).andExpect(redirectedUrl("/transactions"));
        assertNull(repository.findById(row.getId()).getImagePath());
        assertFalse(Files.exists(savedPhoto));
        mvc.perform(get("/transactions")).andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")));
    }
}
