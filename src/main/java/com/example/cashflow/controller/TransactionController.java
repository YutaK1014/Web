package com.example.cashflow.controller;

import com.example.cashflow.dto.TransactionForm;
import com.example.cashflow.dto.TransactionFilter;
import com.example.cashflow.service.TransactionService;
import com.example.cashflow.service.MarketplaceService;
import com.example.cashflow.service.ReportService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.dao.DataAccessException;
import org.slf4j.LoggerFactory;

@Controller
public class TransactionController {
    private final TransactionService service;
    private final MarketplaceService marketplaces;

    public TransactionController(TransactionService service, MarketplaceService marketplaces) {
        this.service = service;
        this.marketplaces = marketplaces;
    }

    @GetMapping("/transactions")
    public String list(@ModelAttribute("filter") TransactionFilter filter, BindingResult errors, Model model) {
        filter.validate(errors);
        var rows = errors.hasErrors() ? List.<com.example.cashflow.entity.Transaction>of() : service.search(filter);
        model.addAttribute("transactions", rows);
        model.addAttribute("summary", ReportService.summarize(rows));
        model.addAttribute("platforms", TransactionForm.PLATFORMS);
        return "transactions";
    }

    @GetMapping("/transactions/new")
    public String legacyCreateForm() {
        return "redirect:/sales/register";
    }

    @GetMapping("/sales/register")
    public String createForm(Model model) {
        model.addAttribute("form", new TransactionForm());
        return prepareForm(null, model);
    }

    @GetMapping("/transactions/{id}/edit")
    public String editForm(@PathVariable long id, Model model) {
        model.addAttribute("form", TransactionForm.from(service.get(id)));
        return prepareForm(id, model);
    }

    private String prepareForm(Long id, Model model) {
        model.addAttribute("transactionId", id);
        model.addAttribute("existingPhoto", id == null ? null : service.get(id).getPhotoUrl());
        model.addAttribute("platforms", TransactionForm.PLATFORMS);
        model.addAttribute("rates", marketplaces.rates());
        model.addAttribute("rounding", marketplaces.rounding());
        return "transaction-form";
    }

    @PostMapping("/transactions")
    public String create(@ModelAttribute("form") TransactionForm form, BindingResult errors,
                         Model model, HttpServletResponse response, RedirectAttributes redirect) {
        return save(null, form, errors, model, response, redirect);
    }

    @PostMapping("/transactions/{id}/edit")
    public String update(@PathVariable long id, @ModelAttribute("form") TransactionForm form, BindingResult errors,
                         Model model, HttpServletResponse response, RedirectAttributes redirect) {
        service.get(id);
        return save(id, form, errors, model, response, redirect);
    }

    private String save(Long id, TransactionForm form, BindingResult errors, Model model,
                        HttpServletResponse response, RedirectAttributes redirect) {
        form.validate(errors);
        if (!errors.hasErrors()) {
            try {
                service.save(id, form);
                redirect.addFlashAttribute("successMessage", id == null ? "取引を登録しました。" : "取引を更新しました。");
                return "redirect:/transactions";
            } catch (IllegalArgumentException exception) {
                errors.reject("invalid", exception.getMessage());
            } catch (IOException | DataAccessException exception) {
                LoggerFactory.getLogger(getClass()).error("取引の保存に失敗しました。", exception);
                response.setStatus(503);
                errors.reject("save", "保存できませんでした。しばらく待ってから再度お試しください。");
            }
        }
        return prepareForm(id, model);
    }

    @GetMapping("/transactions/{id}/delete")
    public String confirmDelete(@PathVariable long id, Model model) {
        model.addAttribute("transaction", service.get(id));
        return "transaction-delete";
    }

    @PostMapping("/transactions/{id}/delete")
    public String delete(@PathVariable long id, RedirectAttributes redirect) {
        service.delete(id);
        redirect.addFlashAttribute("successMessage", "取引を削除しました。");
        return "redirect:/transactions";
    }

    @GetMapping("/transactions/export")
    public void export(@ModelAttribute TransactionFilter filter, BindingResult errors, HttpServletResponse response) throws IOException {
        filter.validate(errors);
        if (errors.hasErrors()) {
            response.sendError(400, "検索条件を確認してください。");
            return;
        }
        var rows = service.search(filter);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=transactions.csv");
        StringBuilder csv = new StringBuilder("\uFEFF商品名,サイト,販売価格,手数料率(%),販売手数料,送料,仕入価格,利益,販売日,メモ,タグ\r\n");
        for (var row : rows) {
            csv.append(csvText(row.getItemName())).append(',').append(csvText(row.getPlatformName())).append(',')
                .append(row.getSellingPrice()).append(',').append(row.getFeeRate()).append(',').append(row.getSellingFee()).append(',')
                .append(row.getShippingCost()).append(',').append(row.getPurchasePrice() == null ? 0 : row.getPurchasePrice()).append(',')
                .append(row.getProfit()).append(',').append(row.getSoldDate()).append(',').append(csvText(row.getMemo())).append(',')
                .append(csvText(String.join(", ", row.getTags()))).append("\r\n");
        }
        response.getOutputStream().write(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String csvText(String value) {
        if (value == null) value = "";
        // Prevent text cells from being interpreted as spreadsheet formulas.
        if (value.stripLeading().matches("(?s)^[=+@-].*") || value.startsWith("\t") || value.startsWith("\r") || value.startsWith("\n")) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
