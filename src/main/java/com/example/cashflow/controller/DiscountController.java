package com.example.cashflow.controller;

import com.example.cashflow.dto.DiscountForm;
import com.example.cashflow.dto.TransactionForm;
import com.example.cashflow.service.DiscountService;
import com.example.cashflow.service.MarketplaceService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/discount-simulator")
public class DiscountController {
    private final DiscountService service;
    private final MarketplaceService marketplaces;

    public DiscountController(DiscountService service, MarketplaceService marketplaces) {
        this.service = service;
        this.marketplaces = marketplaces;
    }

    @GetMapping
    public String show(Model model) {
        model.addAttribute("discountForm", new DiscountForm());
        return prepare(model);
    }

    @PostMapping
    public String calculate(@ModelAttribute("discountForm") DiscountForm form, BindingResult errors, Model model) {
        form.validate(errors);
        if (!errors.hasErrors()) {
            try { model.addAttribute("result", service.calculate(form)); }
            catch (IllegalArgumentException exception) { errors.reject("settings", exception.getMessage()); }
        }
        return prepare(model);
    }

    private String prepare(Model model) {
        model.addAttribute("platforms", TransactionForm.PLATFORMS);
        model.addAttribute("rates", marketplaces.rates());
        return "discount-simulator";
    }
}
