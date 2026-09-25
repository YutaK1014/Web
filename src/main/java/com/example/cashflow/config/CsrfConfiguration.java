package com.example.cashflow.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Local single-user mode still requires CSRF protection for all writes.
@Configuration
public class CsrfConfiguration implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                if (!(handler instanceof org.springframework.web.method.HandlerMethod)) return true;
                var session = request.getSession();
                String expected = (String) session.getAttribute("csrfToken");
                if (expected == null) {
                    expected = UUID.randomUUID().toString();
                    session.setAttribute("csrfToken", expected);
                }
                request.setAttribute("token", expected);
                if (java.util.Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())) return true;
                String actual = request.getParameter("token");
                if (actual == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
                    response.sendError(403, "画面の有効期限が切れました。画面を開き直してください。");
                    return false;
                }
                return true;
            }
        });
    }
}
