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
import org.springframework.mock.web.MockHttpSession;
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
    @Autowired TransactionRepository repository;
    @Autowired MarketplaceService marketplaces;
    @Autowired TransactionService service;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        jdbc.update("DELETE FROM transactions");
        jdbc.update("DELETE FROM marketplace_settings");
        jdbc.update("DELETE FROM app_settings");
        SettingsForm settings = new SettingsForm();
        settings.setMercari("10"); settings.setRakuma("6"); settings.setYahoo("5"); settings.setRounding("DOWN");
        marketplaces.save(settings);
    }

    MockHttpSession session() throws Exception {
        var result = mvc.perform(get("/transactions/new")).andExpect(status().isOk())
            .andExpect(content().string(containsString("Yahoo!フリマ")))
            .andExpect(content().string(containsString("販売日"))).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession();
        assertTrue(result.getResponse().getContentAsString().contains(session.getAttribute("csrfToken").toString()));
        return session;
    }

    MockMultipartHttpServletRequestBuilder valid(String url, MockHttpSession session) {
        var request = multipart(url);
        request.session(session).param("token", session.getAttribute("csrfToken").toString())
            .param("itemName", "商品A").param("sellingPrice","3000").param("shippingCost","750")
            .param("purchasePrice","1000").param("marketplace","メルカリ").param("feeRate","99")
            .param("soldDate","2026-01-02").param("memo","テストメモ");
        return request;
    }

    MockMultipartFile photo() throws Exception {
        var buffer = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",buffer);
        return new MockMultipartFile("photo","../../photo.png","image/png",buffer.toByteArray());
    }

    long create(MockHttpSession session) throws Exception {
        mvc.perform(valid("/transactions",session)).andExpect(redirectedUrl("/transactions"));
        return repository.search("","",null,null).getFirst().getId();
    }

    @Test void savesServerCalculatedFeesDatesAndMemoAndRendersList() throws Exception {
        var session = session();
        long id = create(session);
        var row = repository.findById(id);
        assertEquals(300,row.getSellingFee()); assertEquals(950,row.getProfit());
        assertEquals("2026-01-02",row.getSoldDate().toString()); assertEquals("テストメモ",row.getMemo());
        assertEquals(950L,jdbc.queryForObject("SELECT profit FROM transactions WHERE id=?",Long.class,id));
        mvc.perform(get("/transactions")).andExpect(status().isOk())
            .andExpect(content().string(containsString("950円")))
            .andExpect(content().string(containsString("2026-01-02")))
            .andExpect(content().string(containsString("/transactions/"+id+"/edit")));
    }

    @Test void editRecalculatesAndDeleteRequiresConfirmationPostAndToken() throws Exception {
        var session = session();
        long id = create(session);
        mvc.perform(get("/transactions/"+id+"/edit")).andExpect(status().isOk()).andExpect(content().string(containsString("テストメモ")));
        var request = valid("/transactions/"+id+"/edit",session);
        request.param("sellingPrice","4000");
        // Replace, rather than append, the parameter.
        request.with(req -> { req.setParameter("sellingPrice","4000"); return req; });
        mvc.perform(request).andExpect(redirectedUrl("/transactions"));
        assertEquals(1850,repository.findById(id).getProfit());
        mvc.perform(get("/transactions/"+id+"/delete")).andExpect(status().isOk()).andExpect(content().string(containsString("削除確認")));
        assertNotNull(repository.findById(id));
        mvc.perform(post("/transactions/"+id+"/delete").session(session)).andExpect(status().isForbidden());
        assertNotNull(repository.findById(id));
        mvc.perform(post("/transactions/"+id+"/delete").session(session).param("token",session.getAttribute("csrfToken").toString()))
            .andExpect(redirectedUrl("/transactions"));
        assertNull(repository.findById(id));
        mvc.perform(get("/transactions/"+id+"/edit")).andExpect(status().isNotFound());
    }

    @Test void validatesOtherSiteAndMissingDatesAndPreservesInput() throws Exception {
        var session = session();
        mvc.perform(multipart("/transactions").session(session).param("token",session.getAttribute("csrfToken").toString())
            .param("itemName","入力を保持").param("sellingPrice","1000").param("shippingCost","0").param("marketplace","その他"))
            .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form","soldDate","customMarketplace","feeRate"))
            .andExpect(content().string(containsString("入力を保持")));
        assertTrue(repository.search("","",null,null).isEmpty());
        mvc.perform(multipart("/transactions").session(session).param("token",session.getAttribute("csrfToken").toString())
            .param("itemName","その他取引").param("sellingPrice","1000").param("shippingCost","100")
            .param("marketplace","その他").param("customMarketplace","地域フリマ").param("feeRate","5.25").param("soldDate","2026-03-01"))
            .andExpect(redirectedUrl("/transactions"));
        assertEquals(848,repository.search("","",null,null).getFirst().getProfit());
    }

    @Test void newImageReplacementAndDeletionRemoveFiles() throws Exception {
        var session = session();
        mvc.perform(valid("/transactions",session).file(photo())).andExpect(redirectedUrl("/transactions"));
        var row = repository.search("","",null,null).getFirst();
        Path old = Path.of("target/test-photos",row.getImagePath().substring("/images/".length()));
        assertTrue(Files.exists(old));
        mvc.perform(valid("/transactions/"+row.getId()+"/edit",session).file(photo())).andExpect(redirectedUrl("/transactions"));
        assertFalse(Files.exists(old));
        Path replacement = Path.of("target/test-photos",repository.findById(row.getId()).getImagePath().substring("/images/".length()));
        assertTrue(Files.exists(replacement));
        service.delete(row.getId()); assertFalse(Files.exists(replacement));
    }

    @Test void supportsLiteralKeywordDateAndPlatformFiltersAndCsvEscaping() throws Exception {
        var session = session(); long id = create(session);
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

    @Test void rendersAllPagesAndKeepsExistingAmountsWhenSettingsChange() throws Exception {
        var session = session(); long id = create(session);
        for (String url : List.of("/","/dashboard","/reports?year=2026","/settings","/transactions")) {
            mvc.perform(get(url)).andExpect(status().isOk());
        }
        mvc.perform(post("/settings").session(session).param("token",session.getAttribute("csrfToken").toString())
            .param("mercari","20").param("rakuma","6").param("yahoo","5").param("rounding","HALF_UP").param("monthlyGoal","10000"))
            .andExpect(redirectedUrl("/settings"));
        assertEquals(300,repository.findById(id).getSellingFee());
        assertEquals(10000L,marketplaces.monthlyGoal());
        mvc.perform(valid("/transactions/"+id+"/edit",session)).andExpect(redirectedUrl("/transactions"));
        assertEquals(600,repository.findById(id).getSellingFee());
        mvc.perform(get("/reports").param("year","2026")).andExpect(content().string(containsString("650円")));
    }

    @Test void allWritesRejectMissingCsrfAndRatesMustBeConfigured() throws Exception {
        mvc.perform(post("/settings")).andExpect(status().isForbidden());
        mvc.perform(multipart("/transactions")).andExpect(status().isForbidden());
        mvc.perform(post("/transactions/1/edit")).andExpect(status().isForbidden());
        var session = session();
        jdbc.update("DELETE FROM marketplace_settings");
        mvc.perform(valid("/transactions",session)).andExpect(status().isOk())
            .andExpect(content().string(containsString("設定画面でフリマサイトの手数料率を設定してください")));
        assertTrue(repository.search("","",null,null).isEmpty());
    }

    @Test void rollsBackDatabaseAndNewPhotoTogether() throws Exception {
        var form = new com.example.cashflow.dto.TransactionForm();
        form.setItemName("ロールバック確認"); form.setSellingPrice("1000"); form.setShippingCost("100");
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
        var session = session();
        mvc.perform(valid("/transactions",session).file(photo())).andExpect(redirectedUrl("/transactions"));
        var row = repository.search("","",null,null).getFirst();
        Path savedPhoto = Path.of("target/test-photos",row.getImagePath().substring("/images/".length()));
        var request = valid("/transactions/"+row.getId()+"/edit",session);
        request.param("removePhoto","true");
        request.with(req -> { req.setParameter("itemName","<script>alert(1)</script>"); return req; });
        mvc.perform(request).andExpect(redirectedUrl("/transactions"));
        assertNull(repository.findById(row.getId()).getImagePath());
        assertFalse(Files.exists(savedPhoto));
        mvc.perform(get("/transactions")).andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")));
    }
}
