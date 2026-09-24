package com.example.cashflow;

import com.example.cashflow.controller.TransactionController;
import com.example.cashflow.controller.UploadExceptionHandler;
import com.example.cashflow.dto.TransactionForm;
import com.example.cashflow.entity.Transaction;
import com.example.cashflow.repository.TransactionRepository;
import com.example.cashflow.service.PhotoStorage;
import com.example.cashflow.service.TransactionService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

class TransactionRegistrationTests {
    @TempDir Path directory;
    private TransactionRepository repository;
    private PhotoStorage photos;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        repository = mock(TransactionRepository.class);
        photos = new PhotoStorage(directory.toString());
        ClassLoaderTemplateResolver templates = new ClassLoaderTemplateResolver();
        templates.setPrefix("templates/");
        templates.setSuffix(".html");
        templates.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(templates);
        ThymeleafViewResolver views = new ThymeleafViewResolver();
        views.setTemplateEngine(engine);
        views.setCharacterEncoding("UTF-8");
        mvc = MockMvcBuilders.standaloneSetup(new TransactionController(new TransactionService(repository, photos)))
                .setControllerAdvice(new UploadExceptionHandler()).setViewResolvers(views).build();
    }

    private MockHttpSession session() throws Exception {
        return (MockHttpSession) mvc.perform(get("/transactions/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ヤフーオークション")))
                .andExpect(content().string(containsString("仕入れ価格")))
                .andReturn().getRequest().getSession();
    }

    private MockMultipartFile photo() throws Exception {
        var buffer = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", buffer);
        return new MockMultipartFile("photo", "../../photo.png", "image/png", buffer.toByteArray());
    }

    @Test
    void registersPhotoAndOptionalPurchasePriceThenShowsList() throws Exception {
        MockHttpSession session = session();
        mvc.perform(multipart("/transactions").file(photo()).session(session)
                .param("token", session.getAttribute("transactionToken").toString())
                .param("itemName", "商品A").param("sellingPrice", "3000")
                .param("shippingCost", "750").param("purchasePrice", "")
                .param("marketplace", "その他").param("customMarketplace", ""))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/transactions"));
        var saved = ArgumentCaptor.forClass(Transaction.class);
        verify(repository).insert(saved.capture());
        Transaction row = saved.getValue();
        assertEquals(2250, row.getProfit());
        assertEquals(0, row.purchasePrice());
        assertEquals("その他", row.getPlatformName());
        Path image = directory.resolve(row.imagePath().substring("/images/".length()));
        assertNotNull(ImageIO.read(image.toFile()));
        when(repository.findAll()).thenReturn(List.of(row));
        mvc.perform(get("/transactions")).andExpect(status().isOk())
                .andExpect(content().string(containsString("2,250円")))
                .andExpect(content().string(containsString(row.imagePath())));
    }

    @Test
    void registersPurchasePriceWithoutPhoto() throws Exception {
        MockHttpSession session = session();
        mvc.perform(multipart("/transactions").session(session)
                .param("token", session.getAttribute("transactionToken").toString())
                .param("itemName", "商品B").param("sellingPrice", "3000")
                .param("shippingCost", "750").param("purchasePrice", "1000")
                .param("marketplace", "ヤフーオークション"))
                .andExpect(redirectedUrl("/transactions"));
        var saved = ArgumentCaptor.forClass(Transaction.class);
        verify(repository).insert(saved.capture());
        assertEquals(1250, saved.getValue().getProfit());
        assertNull(saved.getValue().imagePath());
    }

    @Test
    void rejectsInvalidInputAndKeepsEnteredName() throws Exception {
        MockHttpSession session = session();
        mvc.perform(multipart("/transactions").session(session)
                .param("token", session.getAttribute("transactionToken").toString())
                .param("itemName", "入力を保持").param("sellingPrice", "-1")
                .param("shippingCost", "1.5").param("purchasePrice", "2147483648")
                .param("marketplace", "不正な選択"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form",
                        "sellingPrice", "shippingCost", "purchasePrice", "marketplace"))
                .andExpect(content().string(containsString("入力を保持")));
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsMissingTokenWithoutSaving() throws Exception {
        mvc.perform(multipart("/transactions").param("itemName", "商品"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsDisguisedAndOversizedImages() {
        assertThrows(IllegalArgumentException.class, () -> photos.save(
                new MockMultipartFile("photo", "fake.png", "image/png", "not a photo".getBytes())));
        assertThrows(IllegalArgumentException.class, () -> photos.save(
                new MockMultipartFile("photo", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1])));
    }

    @Test
    void redirectsMultipartSizeErrorsToFormWithMessage() throws Exception {
        when(repository.findAll()).thenThrow(new MaxUploadSizeExceededException(5 * 1024 * 1024));
        mvc.perform(get("/transactions"))
                .andExpect(redirectedUrl("/transactions/new"))
                .andExpect(flash().attributeExists("uploadError"));
    }

    @Test
    void removesPhotoIfDatabaseInsertFails() throws Exception {
        TransactionForm form = new TransactionForm();
        form.setItemName("商品");
        form.setSellingPrice("1000");
        form.setShippingCost("100");
        form.setMarketplace("メルカリ");
        form.setPhoto(photo());
        doThrow(new DataAccessResourceFailureException("test")).when(repository).insert(any());
        assertThrows(DataAccessResourceFailureException.class,
                () -> new TransactionService(repository, photos).create(form));
        try (var files = Files.list(directory)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void rejectsBlankNameAndAllowsZeroAmounts() {
        TransactionForm form = new TransactionForm();
        form.setItemName(" ");
        form.setSellingPrice("0");
        form.setShippingCost("0");
        form.setMarketplace("ラクマ");
        var errors = new BeanPropertyBindingResult(form, "form");
        form.validate(errors);
        assertEquals(1, errors.getErrorCount());
        assertTrue(errors.hasFieldErrors("itemName"));
    }
}
