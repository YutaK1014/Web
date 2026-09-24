package com.example.cashflow;

import com.example.cashflow.controller.TransactionController;
import com.example.cashflow.entity.Transaction;
import com.example.cashflow.repository.TransactionRepository;
import com.example.cashflow.service.TransactionService;
import com.example.cashflow.service.PhotoStorage;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.linkbuilder.StandardLinkBuilder;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransactionListTests {
    private Transaction transaction(Integer purchasePrice, String photo) {
        return new Transaction(1, "<script>商品</script>", "その他", "地域のフリマ",
                1000, 100, 200, purchasePrice, LocalDate.of(2026, 9, 24), photo);
    }

    @Test
    void calculatesProfitAndHandlesMissingPurchasePrice() {
        assertEquals(800, transaction(null, null).getProfit());
        assertEquals(-200, transaction(1000, null).getProfit());
        assertEquals("地域のフリマ", transaction(null, null).getPlatformName());
    }

    @Test
    void acceptsOnlyLocalProductPhotos() {
        assertEquals("/images/item-1.jpg", transaction(0, "/images/item-1.jpg").getPhotoUrl());
        assertNull(transaction(0, "https://example.com/photo.jpg").getPhotoUrl());
        assertNull(transaction(0, "/images/../secret.png").getPhotoUrl());
    }

    @Test
    void rendersRowsEmptyStateAndErrorWithoutStartingApplication() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        // Webサーバーを起動せず、ルート配下のリンクとして画面を組み立てる。
        engine.setLinkBuilder(new StandardLinkBuilder() {
            @Override
            protected String computeContextPath(IExpressionContext context, String base,
                    Map<String, Object> parameters) {
                return "";
            }
        });
        Context context = new Context(Locale.JAPAN);
        context.setVariable("transactions", List.of(transaction(null, null), transaction(1000, "/images/item-1.jpg")));
        String html = engine.process("transactions", context);
        assertTrue(html.contains("&lt;script&gt;商品&lt;/script&gt;"));
        assertTrue(html.contains("800円"));
        assertTrue(html.contains("-200円"));
        assertTrue(html.contains("地域のフリマ"));
        assertTrue(html.contains("写真なし"));
        assertTrue(html.contains("src=\"/images/item-1.jpg\""));
        assertFalse(html.contains("取引はまだありません"));

        context.setVariable("transactions", List.of());
        html = engine.process("transactions", context);
        assertTrue(html.contains("取引はまだありません"));
        assertFalse(html.contains("<table>"));

        context.setVariable("errorMessage", "接続できません");
        html = engine.process("transactions", context);
        assertTrue(html.contains("接続できません"));
        assertFalse(html.contains("取引はまだありません"));
    }

    @Test
    void loadsTransactionsAndReportsDatabaseFailure() {
        TransactionRepository repository = mock(TransactionRepository.class);
        TransactionController controller = new TransactionController(new TransactionService(repository, mock(PhotoStorage.class)));
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();
        List<Transaction> rows = List.of(transaction(null, null));
        when(repository.findAll()).thenReturn(rows);
        assertEquals("transactions", controller.list(model, response));
        assertEquals(rows, model.get("transactions"));
        assertEquals(200, response.getStatus());

        when(repository.findAll()).thenThrow(new DataAccessResourceFailureException("test"));
        controller.list(model, response);
        assertEquals(503, response.getStatus());
        assertEquals(List.of(), model.get("transactions"));
        assertNotNull(model.get("errorMessage"));
    }
}
