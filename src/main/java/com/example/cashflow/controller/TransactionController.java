package com.example.cashflow.controller;

import com.example.cashflow.service.TransactionService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TransactionController {
    private static final Logger logger = LoggerFactory.getLogger(TransactionController.class);
    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
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
