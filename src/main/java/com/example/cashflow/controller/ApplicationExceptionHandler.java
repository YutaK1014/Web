package com.example.cashflow.controller;

import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@ControllerAdvice
public class ApplicationExceptionHandler {
    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public String databaseFailure(DataAccessException exception, Model model) {
        LoggerFactory.getLogger(getClass()).error("データベース処理に失敗しました。", exception);
        model.addAttribute("message", "データを読み書きできませんでした。MySQLの起動状態を確認して、もう一度お試しください。");
        return "error";
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String invalidParameter(Model model) {
        model.addAttribute("message", "指定された番号や年が正しくありません。画面から選び直してください。");
        return "error";
    }
}
