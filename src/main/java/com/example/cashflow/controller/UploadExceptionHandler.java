package com.example.cashflow.controller;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class UploadExceptionHandler {
    // ファイルの読み取り段階（Controllerを呼ぶ前）の容量超過にも対応する。
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String uploadTooLarge(RedirectAttributes redirect) {
        redirect.addFlashAttribute("uploadError", "写真は5MB以下にしてください。入力内容と写真を選び直してください。");
        return "redirect:/transactions/new";
    }
}
