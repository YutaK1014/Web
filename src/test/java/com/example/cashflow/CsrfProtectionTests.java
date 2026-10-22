package com.example.cashflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:csrf;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "app.photos.directory=./target/csrf-photos"
})
class CsrfProtectionTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    MockMvc mvc;
    MockHttpSession session;
    String token;

    @BeforeEach void setup() throws Exception {
        // Deliberately no default token: these tests exercise rejected requests too.
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        jdbc.update("DELETE FROM shipping_templates");
        var page = mvc.perform(get("/settings/shipping-templates"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andReturn();
        session = (MockHttpSession) page.getRequest().getSession(false);
        token = CsrfTestSupport.token(page.getResponse().getContentAsString());
    }

    @Test void missingWrongOtherSessionAndExpiredTokensDoNotWrite() throws Exception {
        for (String supplied : new String[]{"", "wrong", token}) {
            var request = post("/settings/shipping-templates")
                .param("name", "不正登録").param("shippingMethod", "宅配便")
                .param("shippingCost", "100").param("packagingCost", "0");
            if (!supplied.isEmpty()) request.param("_csrf", supplied);
            // Correct token with another session must still fail.
            request.session(supplied.equals(token) ? new MockHttpSession() : session);
            mvc.perform(request).andExpect(status().isForbidden())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("画面を開き直して")));
        }
        session.invalidate();
        mvc.perform(post("/settings/shipping-templates").param("_csrf", token))
            .andExpect(status().isForbidden());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM shipping_templates", Integer.class));
    }

    @Test void allMutationRoutesRejectRequestsBeforeBindingOrLookup() throws Exception {
        for (String path : new String[]{"/transactions", "/transactions/999/edit", "/transactions/999/delete",
                "/transactions/999/return", "/purchases", "/purchases/999/tags", "/settings",
                "/settings/shipping-templates", "/settings/shipping-templates/999/edit",
                "/settings/shipping-templates/999/delete", "/discount-simulator"}) {
            mvc.perform(post(path)).andExpect(status().isForbidden());
        }
        mvc.perform(multipart("/transactions").file("photo", new byte[]{1, 2, 3}))
            .andExpect(status().isForbidden());
    }

    @Test void realFormTokenAllowsSaveAndRemainsValidAcrossTabs() throws Exception {
        var second = mvc.perform(get("/settings").session(session)).andExpect(status().isOk()).andReturn();
        assertEquals(token, CsrfTestSupport.token(second.getResponse().getContentAsString()));
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/settings/shipping-templates").session(session).param("_csrf", token)
                .param("name", "正規登録" + i).param("shippingMethod", "宅配便")
                .param("shippingCost", "100").param("packagingCost", "0"))
                .andExpect(status().is3xxRedirection());
        }
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM shipping_templates", Integer.class));
    }

    @Test void duplicateTokensAreRejectedAndReadOnlyRequestsNeedNoToken() throws Exception {
        mvc.perform(post("/settings").session(session).param("_csrf", token, "wrong"))
            .andExpect(status().isForbidden());
        for (String path : new String[]{"/", "/transactions", "/transactions/export", "/healthz"}) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
        String search = mvc.perform(get("/transactions")).andReturn().getResponse().getContentAsString();
        assertFalse(search.contains("name=\"_csrf\""), "GET search forms must not put tokens in URLs");
    }
}
