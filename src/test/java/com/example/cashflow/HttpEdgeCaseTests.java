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

    @Test void malformedFieldMarkersProduceServerErrors() throws Exception {
        assertEquals(500, send("GET", "/transactions?_month=on", "text/plain", new byte[0]).statusCode());
        assertEquals(500, send("POST", "/settings", "application/x-www-form-urlencoded",
            "mercari=10&rakuma=6&yahoo=5&_rounding=on".getBytes(StandardCharsets.UTF_8)).statusCode());
        assertEquals("DOWN", marketplaces.rounding());
    }

    @Test void indexingScalarFieldProducesServerError() throws Exception {
        var response = send("POST", "/purchases", "application/x-www-form-urlencoded",
            "itemName%5B0%5D=x".getBytes(StandardCharsets.UTF_8));
        assertEquals(500, response.statusCode());
        assertTrue(purchases.findAll().isEmpty());
    }

    @Test void oversizedPhotoOnEditRedirectsToNewRegistrationInsteadOfEdit() throws Exception {
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
        assertTrue(response.headers().firstValue("Location").orElseThrow().endsWith("/sales/register"));
        assertEquals(0, sales.findById(id).getVersion());
        assertEquals(1, sales.search("", "", null, null).size());
    }

    @Test void staleTemplateDeleteStillDeletesNewerTemplate() throws Exception {
        jdbc.update("DELETE FROM shipping_templates");
        mvc.perform(post("/settings/shipping-templates").param("name", "元の名前").param("shippingMethod", "宅配便")
            .param("shippingCost", "230").param("packagingCost", "0")).andExpect(status().is3xxRedirection());
        long id = jdbc.queryForObject("SELECT id FROM shipping_templates", Long.class);
        String url = "/settings/shipping-templates/" + id;
        mvc.perform(get(url + "/delete")).andExpect(status().isOk());
        mvc.perform(post(url + "/edit").param("name", "更新済み").param("shippingMethod", "宅配便")
            .param("shippingCost", "750").param("packagingCost", "0")).andExpect(status().is3xxRedirection());
        mvc.perform(post(url + "/delete")).andExpect(status().is3xxRedirection());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM shipping_templates", Integer.class));
    }
}
