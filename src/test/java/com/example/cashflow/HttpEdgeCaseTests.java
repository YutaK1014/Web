package com.example.cashflow;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exploratory assertions describe current behavior, including unhandled errors. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:http-edge;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "app.photos.directory=./target/http-edge-photos"
})
class HttpEdgeCaseTests extends SaveProtectionTests {
    @Value("${local.server.port}") int port;

    HttpResponse<String> send(String method, String path, String type, byte[] body) throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", type)
                .method(method, HttpRequest.BodyPublishers.ofByteArray(body)).build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test void malformedFieldMarkersDisplayValidationErrors() throws Exception {
        for (String field : new String[]{"month", "keyword", "marketplace"}) {
            var response = send("GET", "/transactions?_" + field + "=on", "text/plain", new byte[0]);
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("role=\"alert\""));
        }
        var response = send("POST", "/settings", "application/x-www-form-urlencoded",
            "mercari=10&rakuma=6&yahoo=5&_rounding=on".getBytes(StandardCharsets.UTF_8));
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("手数料の端数処理を選択してください"));
        assertEquals("DOWN", marketplaces.rounding());
    }

    @Test void indexingScalarFieldReturnsBadRequest() throws Exception {
        var response = send("POST", "/purchases", "application/x-www-form-urlencoded",
            "itemName%5B0%5D=x".getBytes(StandardCharsets.UTF_8));
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("入力項目の形式が正しくありません"));
        assertTrue(purchases.findAll().isEmpty());
    }

    @Test void indexedScalarFieldsAreRejectedAcrossFormsWithoutWrites() throws Exception {
        var fields = java.util.Map.of("/transactions", "itemName", "/settings", "mercari",
            "/settings/shipping-templates", "name", "/discount-simulator", "sellingPrice");
        for (var entry : fields.entrySet()) {
            var response = send("POST", entry.getKey(), "application/x-www-form-urlencoded",
                (entry.getValue() + "%5B0%5D=x").getBytes(StandardCharsets.UTF_8));
            assertEquals(400, response.statusCode(), entry.getKey());
            assertFalse(response.body().contains("InvalidPropertyException"));
        }
        assertEquals(400, send("GET", "/transactions?month%5B0%5D=x", "text/plain", new byte[0]).statusCode());
        assertTrue(sales.search("", "", null, null).isEmpty());
        assertEquals("DOWN", marketplaces.rounding());
    }

    @Test void uploadRedirectUsesContextRelativePathAndIgnoresReferer() {
        var request = new org.springframework.mock.web.MockHttpServletRequest("POST", "/app/transactions/123/edit");
        request.setContextPath("/app"); request.addHeader("Referer", "https://untrusted.example/other");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        var model = new org.springframework.ui.ExtendedModelMap();
        var redirect = new org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap();
        var handler = new com.example.cashflow.controller.UploadExceptionHandler();
        assertEquals("redirect:/transactions/123/edit", handler.uploadTooLarge(request, response, model, redirect));
        assertTrue(redirect.getFlashAttributes().get("uploadError").toString().contains("保存していません"));
        request.setRequestURI("/app/transactions");
        assertEquals("redirect:/sales/register", handler.uploadTooLarge(request, response, model, redirect));
        request.setRequestURI("/app/purchases");
        assertEquals("error", handler.uploadTooLarge(request, response, model, redirect));
        assertEquals(413, response.getStatus());
    }

    @Test void totalUploadLimitPreservesEditDestinationAndShowsRecoveryMessage() throws Exception {
        mvc.perform(sale(saleId())).andExpect(status().is3xxRedirection());
        long id = sales.search("", "", null, null).getFirst().getId();
        String path = "/transactions/" + id + "/edit";
        String boundary = "TotalLimitBoundary";
        var body = new java.io.ByteArrayOutputStream();
        for (int part = 0; part < 2; part++) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"large.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(new byte[3 * 1024 * 1024]);
            body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        try (var client = HttpClient.newBuilder().cookieHandler(new java.net.CookieManager()).build()) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Referer", "https://untrusted.example/other")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(302, response.statusCode());
            String location = response.headers().firstValue("Location").orElseThrow();
            assertTrue(location.endsWith(path));
            URI target = URI.create("http://127.0.0.1:" + port).resolve(location);
            var page = client.send(HttpRequest.newBuilder(target).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, page.statusCode());
            assertTrue(page.body().contains("取引の編集"));
            assertTrue(page.body().contains("容量を超えたため保存していません"));
            assertTrue(page.body().contains("data-draft-key=\"sale-" + id + "\""));
        }
        assertEquals(0, sales.findById(id).getVersion());
        assertEquals(3000, sales.findById(id).getSellingPrice());
    }

    @Test void oversizedPhotoOnEditReturnsToSameEditWithoutSaving() throws Exception {
        mvc.perform(sale(saleId())).andExpect(status().is3xxRedirection());
        long id = sales.search("", "", null, null).getFirst().getId();
        String boundary = "EdgeCaseBoundary";
        var body = new java.io.ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"large.png\"\r\n"
            + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(new byte[5 * 1024 * 1024 + 1]);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        var response = send("POST", "/transactions/" + id + "/edit", "multipart/form-data; boundary=" + boundary, body.toByteArray());
        assertEquals(302, response.statusCode());
        assertTrue(response.headers().firstValue("Location").orElseThrow().endsWith("/transactions/" + id + "/edit"));
        assertEquals(0, sales.findById(id).getVersion());
        assertEquals(1, sales.search("", "", null, null).size());
    }

    @Test void staleTemplateDeleteCannotDeleteNewerTemplate() throws Exception {
        jdbc.update("DELETE FROM shipping_templates");
        mvc.perform(post("/settings/shipping-templates").param("name", "元の名前").param("shippingMethod", "宅配便")
            .param("shippingCost", "230").param("packagingCost", "0")).andExpect(status().is3xxRedirection());
        long id = jdbc.queryForObject("SELECT id FROM shipping_templates", Long.class);
        String url = "/settings/shipping-templates/" + id;
        mvc.perform(get(url + "/delete")).andExpect(status().isOk());
        mvc.perform(post(url + "/edit").param("version", "0").param("name", "更新済み").param("shippingMethod", "宅配便")
            .param("shippingCost", "750").param("packagingCost", "0")).andExpect(status().is3xxRedirection());
        mvc.perform(post(url + "/delete").param("version", "0")).andExpect(status().isConflict());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM shipping_templates", Integer.class));
    }
}
