package com.example.cashflow;

import com.example.cashflow.dto.TransactionForm;
import com.example.cashflow.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:draft-identity;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "app.photos.directory=./target/draft-identity-photos"
})
class DatabaseDraftIdentityTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionRepository repository;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = CsrfTestSupport.mvc(context);
        jdbc.update("DELETE FROM transactions");
        jdbc.update("INSERT INTO app_settings(setting_key,setting_value) VALUES('fee_rounding','DOWN') ON DUPLICATE KEY UPDATE setting_value='DOWN'");
        jdbc.update("INSERT INTO transactions(id,item_name,marketplace,selling_price,fee_rate,selling_fee,shipping_cost,purchase_price,profit,sold_date) VALUES(4,'new record','メルカリ',9000,10,900,200,500,7400,'2026-10-27')");
    }

    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder edit(String identity) {
        return post("/transactions/4/edit").param("databaseId", identity).param("version", "0")
            .param("itemName", "old draft").param("marketplace", "その他").param("customMarketplace", "test")
            .param("feeRate", "10").param("sellingPrice", "3000").param("shippingCost", "200")
            .param("purchasePrice", "500").param("soldDate", "2026-10-27");
    }

    @Test void oldDatabaseDraftCannotOverwriteReusedIdEvenAtSameVersion() throws Exception {
        String oldIdentity = repository.databaseId();
        jdbc.update("DELETE FROM database_identity");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(jdbc.getDataSource());
        assertNotEquals(oldIdentity, repository.databaseId());
        mvc.perform(edit(oldIdentity)).andExpect(status().isConflict())
            .andExpect(model().attributeHasErrors("form"));
        assertEquals("new record", repository.findById(4).getItemName());
        assertEquals(9000, repository.findById(4).getSellingPrice());
        assertEquals(0, repository.findById(4).getVersion());
    }

    @Test void missingIdentityIsRejectedAndRetainedOnErrorPage() throws Exception {
        var result = mvc.perform(edit("")).andExpect(status().isConflict()).andReturn();
        assertEquals("", ((TransactionForm) result.getModelAndView().getModel().get("form")).getDatabaseId());
        assertEquals(9000, repository.findById(4).getSellingPrice());
    }

    @Test void schemaRestartPreservesIdentityAndFreshEditWorks() throws Exception {
        String identity = repository.databaseId();
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(jdbc.getDataSource());
        assertEquals(identity, repository.databaseId());
        var result = mvc.perform(get("/transactions/4/edit")).andExpect(status().isOk()).andReturn();
        assertEquals(identity, ((TransactionForm) result.getModelAndView().getModel().get("form")).getDatabaseId());
        mvc.perform(edit(identity)).andExpect(status().is3xxRedirection());
        assertEquals(3000, repository.findById(4).getSellingPrice());
        assertEquals(1, repository.findById(4).getVersion());
    }
}
