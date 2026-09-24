package com.example.cashflow.controller;

import com.example.cashflow.service.TransactionService;
import com.example.cashflow.dto.TransactionForm;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class TransactionController {
    private static final Logger logger = LoggerFactory.getLogger(TransactionController.class);
    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    @GetMapping("/transactions/new")
    public String newTransaction(Model model, HttpSession session) {
        model.addAttribute("form", new TransactionForm());
        prepareForm(model, session);
        return "transaction-form";
    }

    private void prepareForm(Model model, HttpSession session) {
        if (session.getAttribute("transactionToken") == null) {
            session.setAttribute("transactionToken", UUID.randomUUID().toString());
        }
        model.addAttribute("token", session.getAttribute("transactionToken"));
        model.addAttribute("platforms", TransactionForm.PLATFORMS);
    }

    @PostMapping("/transactions")
    public String create(@ModelAttribute("form") TransactionForm form, BindingResult errors,
            @RequestParam(value = "token", required = false) String token,
            Model model, HttpSession session, HttpServletResponse response, RedirectAttributes redirect) {
        prepareForm(model, session);
        if (!session.getAttribute("transactionToken").equals(token)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            errors.reject("token", "画面の有効期限が切れました。内容を確認して、もう一度登録してください。");
            return "transaction-form";
        }
        form.validate(errors);
        if (errors.hasErrors()) return "transaction-form";
        try {
            service.create(form);
        } catch (IllegalArgumentException exception) {
            errors.rejectValue("photo", "invalid", exception.getMessage());
            return "transaction-form";
        } catch (IOException | DataAccessException exception) {
            logger.error("取引の登録に失敗しました。", exception);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            errors.reject("save", "保存できませんでした。MySQLの接続と写真の保存先を確認して、もう一度登録してください。");
            return "transaction-form";
        }
        redirect.addFlashAttribute("successMessage", "取引を登録しました。");
        return "redirect:/transactions";
    }

    @GetMapping({"/", "/transactions"})
    public String list(Model model, HttpServletResponse response) {
        try {
            model.addAttribute("transactions", service.getTransactions());
        } catch (DataAccessException exception) {
            logger.error("取引一覧の取得に失敗しました。", exception);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            model.addAttribute("transactions", List.of());
            model.addAttribute("errorMessage", "取引を読み込めませんでした。MySQLが動いていることと、取引用のテーブルが作成されていることを確認してください。");
        }
        return "transactions";
    }
}
