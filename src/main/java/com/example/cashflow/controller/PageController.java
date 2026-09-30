package com.example.cashflow.controller;

import com.example.cashflow.dto.SettingsForm;
import com.example.cashflow.dto.PurchaseForm;
import com.example.cashflow.dto.TagForm;
import com.example.cashflow.service.PurchaseService;
import com.example.cashflow.service.MarketplaceService;
import com.example.cashflow.service.ReportService;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Controller
public class PageController {
    private final ReportService reports;
    private final MarketplaceService marketplaces;
    private final PurchaseService purchases;
    public PageController(ReportService reports, MarketplaceService marketplaces, PurchaseService purchases) {
        this.reports = reports;
        this.marketplaces = marketplaces;
        this.purchases = purchases;
    }

    @GetMapping("/")
    public String home(@RequestParam(defaultValue = "") String tag, Model model) {
        tag = tag.strip();
        if (tag.length() > 30) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "検索タグは30文字以内で入力してください。");
        model.addAttribute("purchaseForm", new PurchaseForm());
        model.addAttribute("tag", tag);
        model.addAttribute("purchases", purchases.search(tag));
        return "home";
    }

    @PostMapping("/purchases")
    public String savePurchase(@ModelAttribute("purchaseForm") PurchaseForm form, BindingResult errors,
                               Model model, RedirectAttributes redirect) {
        purchases.save(form, errors);
        if (errors.hasErrors()) {
            model.addAttribute("tag", "");
            model.addAttribute("purchases", purchases.all());
            return "home";
        }
        redirect.addFlashAttribute("successMessage", "購入履歴を登録しました。");
        return "redirect:/#purchase-history";
    }

    @GetMapping("/purchases/{id}/tags")
    public String purchaseTags(@PathVariable long id, Model model) {
        var purchase = purchases.get(id);
        var form = new TagForm();
        form.setTags(String.join(", ", purchase.getTags()));
        model.addAttribute("purchase", purchase);
        model.addAttribute("tagForm", form);
        return "purchase-tags";
    }

    @PostMapping("/purchases/{id}/tags")
    public String savePurchaseTags(@PathVariable long id,
            @ModelAttribute("tagForm") TagForm form,
            BindingResult errors, Model model, RedirectAttributes redirect) {
        model.addAttribute("purchase", purchases.get(id));
        TagForm.validate(form.getTags(), errors);
        if (errors.hasErrors()) return "purchase-tags";
        purchases.updateTags(id, form.getTags());
        redirect.addFlashAttribute("successMessage", "購入履歴のタグを更新しました。");
        return "redirect:/#purchase-history";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        var all = reports.all();
        YearMonth now = YearMonth.now();
        var current = all.stream().filter(t -> YearMonth.from(t.getSoldDate()).equals(now)).toList();
        model.addAttribute("month", now);
        model.addAttribute("summary", ReportService.summarize(current));
        model.addAttribute("recent", all.stream().limit(5).toList());
        model.addAttribute("chart", reports.chart(reports.monthly(all, now.getYear())));
        model.addAttribute("marketplaces", reports.byMarketplace(current));
        model.addAttribute("goal", marketplaces.monthlyGoal());
        return "dashboard";
    }

    @GetMapping("/reports")
    public String reports(@RequestParam(required = false) Integer year, Model model) {
        int selected = year == null ? LocalDate.now().getYear() : year;
        if (selected < 1000 || selected > 9999) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "年を正しく入力してください。");
        var all = reports.all();
        var annual = all.stream().filter(t -> t.getSoldDate().getYear() == selected).toList();
        var monthly = reports.monthly(all, selected);
        model.addAttribute("year", selected);
        model.addAttribute("summary", ReportService.summarize(annual));
        model.addAttribute("monthly", monthly);
        model.addAttribute("yearly", reports.yearly(all));
        model.addAttribute("marketplaces", reports.byMarketplace(annual));
        model.addAttribute("chart", reports.chart(monthly));
        return "reports";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("settings", marketplaces.settings());
        return "settings";
    }

    @PostMapping("/settings")
    public String saveSettings(@ModelAttribute("settings") SettingsForm form, BindingResult errors, RedirectAttributes redirect) {
        form.validate(errors);
        if (errors.hasErrors()) return "settings";
        marketplaces.save(form);
        redirect.addFlashAttribute("successMessage", "設定を保存しました。保存済みの取引の金額は変更していません。");
        return "redirect:/settings";
    }
}
