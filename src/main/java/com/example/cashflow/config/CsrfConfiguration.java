package com.example.cashflow.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.support.RequestDataValueProcessor;

/** Session-bound synchronizer tokens for every MVC state-changing request. */
@Configuration
public class CsrfConfiguration implements WebMvcConfigurer {
    private static final String SESSION_KEY = CsrfConfiguration.class.getName() + ".token";
    private static final String FORM_METHOD = CsrfConfiguration.class.getName() + ".formMethod";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final SecureRandom RANDOM = new SecureRandom();

    private static String token(HttpServletRequest request) {
        var session = request.getSession();
        synchronized (session) {
            String token = (String) session.getAttribute(SESSION_KEY);
            if (token == null) {
                byte[] bytes = new byte[32];
                RANDOM.nextBytes(bytes);
                token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                session.setAttribute(SESSION_KEY, token);
            }
            return token;
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Runs after multipart resolution, but before binding or invoking any controller.
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws Exception {
                if (SAFE_METHODS.contains(request.getMethod())) return true;
                var session = request.getSession(false);
                String expected = session == null ? null : (String) session.getAttribute(SESSION_KEY);
                String[] values = request.getParameterValues("_csrf");
                if (expected != null && values != null && values.length == 1
                        && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                            values[0].getBytes(StandardCharsets.UTF_8))) return true;
                response.setHeader("Cache-Control", "no-store");
                throw new CsrfRejectedException();
            }
        }).order(Integer.MIN_VALUE);
    }

    public static class CsrfRejectedException extends RuntimeException {}

    @Bean(name = "requestDataValueProcessor")
    public RequestDataValueProcessor requestDataValueProcessor() {
        return new RequestDataValueProcessor() {
            @Override
            public String processAction(HttpServletRequest request, String action, String httpMethod) {
                request.setAttribute(FORM_METHOD, httpMethod.toUpperCase(java.util.Locale.ROOT));
                return action;
            }

            @Override
            public String processFormFieldValue(HttpServletRequest request, String name, String value, String type) {
                return value;
            }

            @Override
            public Map<String, String> getExtraHiddenFields(HttpServletRequest request) {
                Object method = request.getAttribute(FORM_METHOD);
                if (method == null || SAFE_METHODS.contains(method)) return Map.of();
                // A token-bearing page must never be shared by intermediary caches.
                var attributes = (org.springframework.web.context.request.ServletRequestAttributes)
                    org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes();
                var response = attributes.getResponse();
                if (response != null) response.setHeader("Cache-Control", "no-store");
                return Map.of("_csrf", token(request));
            }

            @Override
            public String processUrl(HttpServletRequest request, String url) { return url; }
        };
    }
}
