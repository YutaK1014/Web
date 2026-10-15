package com.example.cashflow.controller;

import com.example.cashflow.dto.ShippingTemplateForm;
import com.example.cashflow.service.ShippingTemplateService;
import com.example.cashflow.service.SaveConflictException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/settings/shipping-templates")
public class ShippingTemplateController {
    private final ShippingTemplateService service;
    public ShippingTemplateController(ShippingTemplateService service) { this.service = service; }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("templateForm", new ShippingTemplateForm());
        return prepare(null, model);
    }
    @GetMapping("/{id}/edit")
    public String edit(@PathVariable long id, Model model) {
        model.addAttribute("templateForm", ShippingTemplateForm.from(service.get(id)));
        return prepare(id, model);
    }
    @PostMapping
    public String create(@ModelAttribute("templateForm") ShippingTemplateForm form, BindingResult errors,
                         Model model, RedirectAttributes redirect, HttpServletResponse response) {
        return save(null, form, errors, model, redirect, response);
    }
    @PostMapping("/{id}/edit")
    public String update(@PathVariable long id, @ModelAttribute("templateForm") ShippingTemplateForm form,
                         BindingResult errors, Model model, RedirectAttributes redirect, HttpServletResponse response) {
        service.get(id);
        return save(id, form, errors, model, redirect, response);
    }
    private String save(Long id, ShippingTemplateForm form, BindingResult errors, Model model, RedirectAttributes redirect, HttpServletResponse response) {
        form.validate(errors);
        if (errors.hasErrors()) return prepare(id, model);
        try {
            service.save(id, form);
        } catch (SaveConflictException exception) {
            response.setStatus(409);
            errors.reject("conflict", exception.getMessage());
            model.addAttribute("templateConflict", true);
            return prepare(id, model);
        }
        redirect.addFlashAttribute("successMessage", "送料・梱包テンプレートを保存しました。");
        return "redirect:/settings/shipping-templates";
    }
    private String prepare(Long id, Model model) {
        model.addAttribute("templateId", id);
        model.addAttribute("shippingTemplates", service.all());
        return "shipping-templates";
    }
    @GetMapping("/{id}/delete")
    public String confirmDelete(@PathVariable long id, Model model) {
        model.addAttribute("template", service.get(id));
        return "shipping-template-delete";
    }
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id, @RequestParam(required = false) Long version,
                         Model model, HttpServletResponse response, RedirectAttributes redirect) {
        try {
            service.delete(id, version);
        } catch (SaveConflictException exception) {
            response.setStatus(409);
            model.addAttribute("template", service.get(id));
            model.addAttribute("deleteConflict", exception.getMessage());
            return "shipping-template-delete";
        }
        redirect.addFlashAttribute("successMessage", "テンプレートを削除しました。登録済み取引の金額は変わりません。");
        return "redirect:/settings/shipping-templates";
    }
}
