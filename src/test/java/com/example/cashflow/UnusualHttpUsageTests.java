package com.example.cashflow;

import com.example.cashflow.dto.SettingsForm;
import com.example.cashflow.service.MarketplaceService;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Bounded exploratory HTTP checks against a disposable database, never personal data. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:unusual-http;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password=", "app.photos.directory=./target/unusual-http-photos"
})
class UnusualHttpUsageTests {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired MarketplaceService marketplaces;
    HttpClient client;
    CookieManager cookies;
    String csrf;
    static final Path EVIDENCE = Path.of("target/unusual-http-results.txt");

    @BeforeAll static void clearEvidence() throws Exception { Files.writeString(EVIDENCE, "Exploratory HTTP results\n"); }
    @BeforeEach void setup() throws Exception {
        jdbc.update("DELETE FROM transactions");
        jdbc.update("DELETE FROM purchases");
        jdbc.update("DELETE FROM registration_submissions");
        jdbc.update("DELETE FROM shipping_templates");
        var settings = new SettingsForm();
        settings.setMercari("10"); settings.setRakuma("6"); settings.setYahoo("5");
        settings.setRounding("DOWN"); settings.setVersion(marketplaces.settings().getVersion());
        marketplaces.save(settings);
        cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        client = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
        csrf = CsrfTestSupport.token(send("GET", "/settings", "text/plain", "").body());
    }
    @AfterEach void close() { client.close(); }
    HttpResponse<String> send(String method, String path, String type, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(15)).header("Content-Type", type)
            .method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    String fields(String id) {
        return "_csrf=" + csrf + "&submissionId=" + id + "&itemName=probe&marketplace=" + enc("メルカリ")
            + "&sellingPrice=3000&shippingCost=750&purchasePrice=1000&soldDate=2026-10-23";
    }
    HttpResponse<String> post(String path, String body) throws Exception {
        return send("POST", path, "application/x-www-form-urlencoded;charset=UTF-8", body);
    }
    int count() { return jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class); }
    static synchronized void record(String text) throws Exception {
        Files.writeString(EVIDENCE, text + "\n", StandardOpenOption.APPEND);
    }

    @Test void duplicateAmountParametersCannotSilentlyChooseAnAmount() throws Exception {
        for (String extra : new String[]{"&sellingPrice=1", "&shippingCost=0", "&purchasePrice=0"}) {
            var response = post("/transactions", fields(UUID.randomUUID().toString()) + extra);
            record("duplicate money " + extra + ": HTTP " + response.statusCode() + ", rows=" + count());
            assertTrue(response.statusCode() < 500);
            assertEquals(0, count());
        }
    }

    @Test void nestedAndIndexedScalarInputsNeverCauseServerErrors() throws Exception {
        var failures = new ArrayList<String>();
        for (String key : new String[]{"itemName[0]", "itemName.value", "sellingPrice[999999]",
                "version.value", "photo[0]", "tags[0]", "removePhoto[0]"}) {
            var response = post("/transactions", fields(UUID.randomUUID().toString()) + "&" + enc(key) + "=x");
            record("binding shape " + key + ": HTTP " + response.statusCode() + ", rows=" + count());
            if (response.statusCode() >= 500) failures.add(key + ": " + response.statusCode());
            jdbc.update("DELETE FROM transactions");
        }
        assertTrue(failures.isEmpty(), "Unhandled binding shapes: " + failures);
    }

    @Test void malformedMultipartIsAClientErrorAndCannotWrite() throws Exception {
        assertEquals(302, post("/transactions", fields(UUID.randomUUID().toString())).statusCode());
        var before = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (String table : new String[]{"transactions", "purchases", "marketplace_settings",
                "app_settings", "settings_version", "registration_submissions"}) {
            before.put(table, jdbc.queryForList("SELECT * FROM " + table));
        }
        for (String path : new String[]{"/transactions", "/purchases", "/settings"}) {
            for (String type : new String[]{"multipart/form-data", "multipart/form-data; boundary=broken"}) {
                var response = send("POST", path, type, "not a multipart body");
                record("malformed multipart " + path + " " + type + ": HTTP " + response.statusCode());
                if (type.equals("multipart/form-data")) {
                    assertEquals(400, response.statusCode(), path);
                    assertTrue(response.body().contains("送信形式が正しくないため保存できませんでした。"));
                    assertTrue(response.body().contains("画面を開き直し、写真を選び直してください。"));
                } else {
                    assertEquals(403, response.statusCode(), "Empty parsed form still requires CSRF");
                }
                assertFalse(response.body().contains("org.apache.tomcat"), "Exception detail leaked");
                for (var entry : before.entrySet()) {
                    assertEquals(entry.getValue(), jdbc.queryForList("SELECT * FROM " + entry.getKey()), entry.getKey());
                }
            }
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM purchases", Integer.class));
        assertEquals(302, post("/transactions", fields(UUID.randomUUID().toString())).statusCode());
        assertEquals(2, count());
        record("Normal sale after malformed requests succeeds; error response contains no Tomcat exception name");
    }

    @Test void jsonBodyAndUnsupportedMethodsCannotChangeStoredData() throws Exception {
        var response = send("POST", "/transactions?_csrf=" + csrf, "application/json", "{\"sellingPrice\":1,\"profit\":999999}");
        assertTrue(response.statusCode() < 500);
        assertEquals(0, count());
        for (String method : new String[]{"PUT", "PATCH", "DELETE"}) {
            response = send(method, "/transactions", "application/x-www-form-urlencoded", fields(UUID.randomUUID().toString()));
            assertTrue(response.statusCode() >= 400 && response.statusCode() < 500);
        }
        assertEquals(0, count());
        record("JSON body and PUT/PATCH/DELETE did not write");
    }

    @Test void getHeadAndOptionsOnDeleteConfirmationNeverDelete() throws Exception {
        assertEquals(302, post("/transactions", fields(UUID.randomUUID().toString())).statusCode());
        long id = jdbc.queryForObject("SELECT id FROM transactions", Long.class);
        for (String method : new String[]{"GET", "HEAD", "OPTIONS"}) {
            var response = send(method, "/transactions/" + id + "/delete?version=0", "text/plain", "");
            assertTrue(response.statusCode() < 500);
            assertEquals(1, count());
        }
        record("GET/HEAD/OPTIONS delete-confirmation requests preserved the row");
    }

    @Test void oldTokenAfterCookieLossCannotSaveEvenWithFreshSession() throws Exception {
        String old = csrf;
        cookies.getCookieStore().removeAll();
        csrf = CsrfTestSupport.token(send("GET", "/settings", "text/plain", "").body());
        assertNotEquals(old, csrf);
        var response = post("/transactions", fields(UUID.randomUUID().toString()).replace(csrf, old));
        assertEquals(403, response.statusCode());
        assertEquals(0, count());
        assertEquals(302, post("/transactions", fields(UUID.randomUUID().toString())).statusCode());
        record("Cookie loss: old token 403; newly opened form succeeds");
    }

    @Test void csrfQueryAndBodyConflictsCannotBypassValidation() throws Exception {
        var response = post("/transactions?_csrf=wrong", fields(UUID.randomUUID().toString()));
        assertEquals(403, response.statusCode());
        response = post("/transactions?_csrf=" + csrf, fields(UUID.randomUUID().toString()).replace(csrf, "wrong"));
        assertEquals(403, response.statusCode());
        assertEquals(0, count());
        record("Conflicting query/body CSRF parameters rejected in both orders");
    }

    @Test void twelveConcurrentReplaysHaveExactlyOneWinner() throws Exception {
        String body = fields(UUID.randomUUID().toString());
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(6)) {
            var futures = new ArrayList<Future<Integer>>();
            for (int i=0; i<12; i++) futures.add(executor.submit(() -> { gate.await(); return post("/transactions", body).statusCode(); }));
            gate.countDown();
            var statuses = new ArrayList<Integer>();
            for (var future : futures) statuses.add(future.get(25, TimeUnit.SECONDS));
            record("12 concurrent replays: " + statuses + ", rows=" + count());
            assertEquals(1, statuses.stream().filter(s -> s == 302).count());
            assertEquals(11, statuses.stream().filter(s -> s == 409).count());
            assertEquals(1, count());
        }
    }

    @Test void oversizedMemoAndEmojiNamesNeverPartiallySave() throws Exception {
        for (String extra : new String[]{"memo=" + "x".repeat(10001), "itemName=" + enc("😀".repeat(51))}) {
            String body = fields(UUID.randomUUID().toString());
            if (extra.startsWith("itemName")) body = body.replace("itemName=probe", extra);
            else body += "&" + extra;
            var response = post("/transactions", body);
            assertTrue(response.statusCode() < 500);
            assertEquals(0, count());
        }
        record("Over-limit memo and 51-emoji name rejected without saving");
    }

    @Test void sqlAndHtmlPayloadsRemainLiteralText() throws Exception {
        String payload = "<img src=x onerror=alert(1)> ' OR 1=1 --";
        var response = post("/transactions", fields(UUID.randomUUID().toString()).replace("itemName=probe", "itemName=" + enc(payload)));
        assertEquals(302, response.statusCode());
        response = send("GET", "/transactions?keyword=" + enc(payload), "text/plain", "");
        assertEquals(200, response.statusCode());
        assertFalse(response.body().contains("<img src=x onerror=alert(1)>"));
        assertTrue(response.body().contains("&lt;img"));
        assertEquals(1, count());
        record("SQL/HTML-looking item name stored and searched as escaped literal text");
    }
}
