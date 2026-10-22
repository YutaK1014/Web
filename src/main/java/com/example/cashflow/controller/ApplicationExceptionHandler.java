package com.example.cashflow.controller;

import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@ControllerAdvice
public class ApplicationExceptionHandler {
    @ExceptionHandler(com.example.cashflow.config.CsrfConfiguration.CsrfRejectedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String invalidSubmission(Model model) {
        model.addAttribute("message", "送信を確認できなかったため、保存・変更は行いませんでした。画面を開き直して再度お試しください。入力途中の内容は、下書きがあれば復元できます。写真は選び直してください。");
        return "error";
    }

    @ExceptionHandler(org.springframework.beans.InvalidPropertyException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String invalidField(Model model) {
        model.addAttribute("message", "入力項目の形式が正しくありません。画面を開き直して入力してください。");
        return "error";
    }

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public String databaseFailure(DataAccessException exception, Model model) {
        LoggerFactory.getLogger(getClass()).error("データベース処理に失敗しました。", exception);
        model.addAttribute("message", "データを読み書きできませんでした。しばらく待ってから、もう一度お試しください。");
        return "error";
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String invalidParameter(Model model) {
        model.addAttribute("message", "指定された番号や年が正しくありません。画面から選び直してください。");
        return "error";
    }
}
