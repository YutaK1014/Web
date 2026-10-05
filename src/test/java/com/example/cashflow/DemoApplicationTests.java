package com.example.cashflow;

import com.example.cashflow.controller.HealthController;
import java.sql.SQLException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.annotation.Transactional;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "app.photos.directory=./target/demo-test-photos")
@ActiveProfiles("demo")
class DemoApplicationTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Value("${local.server.port}") int port;

    @Test void dashboardRendersSelectedMonthComparisonCalendarAndDailyLinks() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        String date = jdbc.queryForObject("SELECT sold_date FROM transactions WHERE id=1", java.sql.Date.class).toString();
        mvc.perform(get("/dashboard").param("month", date.substring(0, 7)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("前月との比較")))
            .andExpect(content().string(containsString("平均利益")))
            .andExpect(content().string(containsString("販売カレンダー")))
            .andExpect(content().string(containsString("/transactions?from=" + date + "&amp;to=" + date)));
        for (String month : new String[]{"2024-02", "1000-01", "9999-12"}) {
            mvc.perform(get("/dashboard").param("month", month)).andExpect(status().isOk())
                .andExpect(content().string(containsString("この月の販売はありません。")));
        }
        for (String month : new String[]{"", "wrong", "2026-13", "0999-12", "10000-01"}) {
            mvc.perform(get("/dashboard").param("month", month)).andExpect(status().isBadRequest());
        }
    }

    @Test void firstPostBehindHttpsProxyRedirectsWithoutUrlSessionId() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/settings"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "demo.example.test")
                .header("X-Forwarded-Port", "443")
                .POST(HttpRequest.BodyPublishers.ofString(
                    "mercari=10&rakuma=10&yahoo=5&rounding=DOWN&monthlyGoal=10000"))
                .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(302, response.statusCode());
            assertEquals("https://demo.example.test/settings", response.headers().firstValue("Location").orElseThrow());
            assertTrue(response.headers().firstValue("Set-Cookie").orElseThrow().contains("Secure"));
        }
    }

    @Test void demoStartsWithSamplesAndAllMainPagesAreAccessible() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM purchases", Integer.class));
        for (String page : new String[]{"/", "/dashboard", "/transactions", "/sales/register",
                "/transactions/1/edit", "/transactions/1/delete", "/reports", "/settings", "/purchases/1/tags"}) {
            mvc.perform(get(page)).andExpect(status().isOk())
                .andExpect(content().string(containsString("公開デモ")));
        }
        mvc.perform(get("/transactions/export").param("tag", "衣類"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("デモ：スニーカー")));
        mvc.perform(get("/healthz")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test @Transactional void visitorCanCreateEditAndDeleteWithoutInitialSetup() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        mvc.perform(post("/transactions").param("itemName", "体験用の本")
            .param("marketplace", "メルカリ").param("sellingPrice", "3000")
            .param("shippingCost", "210").param("purchasePrice", "500")
            .param("soldDate", "2026-10-01").param("tags", "体験"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/transactions"));
        long id = jdbc.queryForObject("SELECT id FROM transactions WHERE item_name='体験用の本'", Long.class);
        assertEquals(1990, jdbc.queryForObject("SELECT profit FROM transactions WHERE id=?", Integer.class, id));
        mvc.perform(post("/transactions/" + id + "/edit").param("itemName", "更新した本")
            .param("marketplace", "メルカリ").param("sellingPrice", "4000")
            .param("shippingCost", "210").param("purchasePrice", "500").param("soldDate", "2026-10-01"))
            .andExpect(status().is3xxRedirection());
        assertEquals(2890, jdbc.queryForObject("SELECT profit FROM transactions WHERE id=?", Integer.class, id));
        mvc.perform(post("/transactions/" + id + "/delete")).andExpect(status().is3xxRedirection());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE id=?", Integer.class, id));
    }

    @Test void healthReturnsUnavailableWithoutLeakingDatabaseDetails() throws Exception {
        DataSource unavailable = mock(DataSource.class);
        when(unavailable.getConnection()).thenThrow(new SQLException("private connection details"));
        var mvc = MockMvcBuilders.standaloneSetup(new HealthController(unavailable)).build();
        mvc.perform(get("/healthz")).andExpect(status().isServiceUnavailable())
            .andExpect(content().json("{\"status\":\"DOWN\"}"));
    }
}
