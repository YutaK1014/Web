package com.example.cashflow;

import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.util.regex.Pattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Existing business tests use a real rendered token and its browser session. */
final class CsrfTestSupport {
    static String token(String html) {
        var matcher = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(html);
        if (!matcher.find()) throw new AssertionError("Rendered CSRF token missing");
        return matcher.group(1);
    }

    static MockMvc mvc(WebApplicationContext context) {
        try {
            var initial = MockMvcBuilders.webAppContextSetup(context).build()
                .perform(get("/settings")).andReturn();
            String token = token(initial.getResponse().getContentAsString());
            var session = (MockHttpSession) initial.getRequest().getSession(false);
            return MockMvcBuilders.webAppContextSetup(context)
                .defaultRequest(get("/").session(session).param("_csrf", token)
                    .param("databaseId", context.getBean(com.example.cashflow.repository.TransactionRepository.class).databaseId())).build();
        } catch (Exception exception) {
            throw new AssertionError("Unable to open a protected form", exception);
        }
    }
}
