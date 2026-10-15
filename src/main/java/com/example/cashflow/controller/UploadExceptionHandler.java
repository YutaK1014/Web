package com.example.cashflow.controller;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ui.Model;

@ControllerAdvice
public class UploadExceptionHandler {
    // ファイルの読み取り段階（Controllerを呼ぶ前）の容量超過にも対応する。
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String uploadTooLarge(HttpServletRequest request, HttpServletResponse response,
                                 Model model, RedirectAttributes redirect) {
        // The container rejects the body before binding; do not parse it again or trust Referer.
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String destination;
        if (path.matches("/transactions/[1-9][0-9]*/edit")) destination = path;
        else if ("/transactions".equals(path)) destination = "/sales/register";
        else {
            response.setStatus(413);
            model.addAttribute("message", "送信データの容量が大きすぎます。写真は1枚5MB以下、送信全体は6MB以下にしてください。");
            return "error";
        }
        redirect.addFlashAttribute("uploadError", "容量を超えたため保存していません。写真は1枚5MB以下、送信全体は6MB以下にしてください。下書きがあれば復元し、なければ入力し直してください。写真は選び直してください。");
        return "redirect:" + destination;
    }
}
