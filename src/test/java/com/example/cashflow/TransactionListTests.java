package com.example.cashflow;

import com.example.cashflow.dto.TransactionForm;
import com.example.cashflow.dto.TransactionFilter;
import com.example.cashflow.entity.Transaction;
import com.example.cashflow.repository.MarketplaceSettingRepository;
import com.example.cashflow.repository.TransactionRepository;
import com.example.cashflow.service.MarketplaceService;
import com.example.cashflow.service.ReportService;
import com.example.cashflow.service.PhotoStorage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.validation.BeanPropertyBindingResult;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransactionListTests {
    @TempDir Path directory;

    private Transaction row(int price, int fee, int shipping, Integer purchase, String date, String market) {
        Transaction row = new Transaction();
        row.setSellingPrice(price); row.setSellingFee(fee); row.setShippingCost(shipping);
        row.setPurchasePrice(purchase); row.setSoldDate(LocalDate.parse(date)); row.setMarketplace(market);
        return row;
    }

    @Test void deductsFeesAndSupportsLargeLosses() {
        assertEquals(700, row(1000,100,200,null,"2026-01-01","メルカリ").getProfit());
        assertEquals(-300, row(1000,100,200,1000,"2026-01-01","メルカリ").getProfit());
        assertEquals(-3_000_000_000L, row(0,1_000_000_000,1_000_000_000,1_000_000_000,"2026-01-01","メルカリ").getProfit());
    }

    @Test void validatesOtherSiteDateAndAmounts() {
        TransactionForm form = new TransactionForm();
        form.setItemName("商品"); form.setMarketplace("その他"); form.setSellingPrice("0"); form.setShippingCost("0");
        var errors = new BeanPropertyBindingResult(form, "form");
        form.validate(errors);
        assertTrue(errors.hasFieldErrors("soldDate"));
        assertTrue(errors.hasFieldErrors("customMarketplace"));
        assertTrue(errors.hasFieldErrors("feeRate"));
        form.setSoldDate("2026-02-28"); form.setCustomMarketplace("地域のフリマ"); form.setFeeRate("5.25");
        errors = new BeanPropertyBindingResult(form, "form"); form.validate(errors); assertFalse(errors.hasErrors());
        form.setSoldDate("2026-02-30"); form.setSellingPrice("-1"); form.setFeeRate("100.01");
        errors = new BeanPropertyBindingResult(form, "form"); form.validate(errors);
        assertTrue(errors.hasFieldErrors("soldDate")); assertTrue(errors.hasFieldErrors("sellingPrice")); assertTrue(errors.hasFieldErrors("feeRate"));
    }

    @Test void calculatesDecimalFeesWithExplicitRounding() {
        var repository = mock(MarketplaceSettingRepository.class);
        var service = new MarketplaceService(repository);
        when(repository.getOption("fee_rounding")).thenReturn("DOWN");
        assertEquals(99, service.calculateFee(999, new BigDecimal("10")));
        assertEquals(52, service.calculateFee(1000, new BigDecimal("5.25")));
        when(repository.getOption("fee_rounding")).thenReturn("HALF_UP");
        assertEquals(53, service.calculateFee(1000, new BigDecimal("5.25")));
        when(repository.getOption("fee_rounding")).thenReturn("UP");
        assertEquals(53, service.calculateFee(1000, new BigDecimal("5.25")));
        when(repository.getOption("fee_rounding")).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.calculateFee(1000, BigDecimal.TEN));
    }

    @Test void summarizesBySaleDateAndSeparatesCustomMarketplaces() {
        var rows = List.of(row(1000,100,200,null,"2025-12-31","メルカリ"),
                          row(2000,200,300,100,"2026-01-01","ラクマ"),
                          row(500,50,200,400,"2026-02-01","その他"));
        rows.get(2).setCustomMarketplace("地域");
        var service = new ReportService(mock(TransactionRepository.class));
        var total = ReportService.summarize(rows);
        assertEquals(3500,total.sales()); assertEquals(1950,total.profit()); assertEquals(350,total.fees());
        assertEquals(700,total.shipping()); assertEquals(500,total.purchases()); assertEquals(3,total.count());
        assertEquals(12, service.monthly(rows,2026).size());
        assertEquals(1400, service.monthly(rows,2026).get(0).summary().profit());
        assertEquals(-150, service.monthly(rows,2026).get(1).summary().profit());
        assertEquals(2,service.yearly(rows).size());
        assertTrue(service.byMarketplace(rows).stream().anyMatch(g -> g.label().equals("地域")));
        assertEquals(0, service.chart(service.monthly(List.of(),2026)).get(0).width());
    }

    @Test void intersectsMonthAndDateFilters() {
        TransactionFilter filter = new TransactionFilter();
        filter.setMonth("2024-02"); filter.setFrom("2024-02-10"); filter.setTo("2024-03-01");
        assertEquals(LocalDate.of(2024,2,10),filter.start());
        assertEquals(LocalDate.of(2024,2,29),filter.end());
        filter.setFrom("2024-03-01");
        var errors = new BeanPropertyBindingResult(filter,"filter"); filter.validate(errors); assertTrue(errors.hasErrors());
    }

    @Test void acceptsWebpAndRejectsFakeOversizedOrUnsafePhotos() throws Exception {
        PhotoStorage storage = new PhotoStorage(directory.toString());
        // A real 1x1 lossless WebP fixture.
        byte[] webp = Base64.getDecoder().decode("UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==");
        String path = storage.save(new MockMultipartFile("photo","one.webp","image/webp",webp));
        assertNotNull(ImageIO.read(directory.resolve(path.substring("/images/".length())).toFile()));
        storage.delete(path);
        assertFalse(java.nio.file.Files.exists(directory.resolve(path.substring("/images/".length()))));
        assertThrows(IllegalArgumentException.class, () -> storage.save(new MockMultipartFile("photo","fake.png","image/png","fake".getBytes())));
        assertThrows(IllegalArgumentException.class, () -> storage.save(new MockMultipartFile("photo","large.png","image/png",new byte[5*1024*1024+1])));
        Transaction row = new Transaction(); row.setImagePath("/images/../secret.png"); assertNull(row.getPhotoUrl());
        row.setImagePath("https://example.com/image.png"); assertNull(row.getPhotoUrl());
    }
}
