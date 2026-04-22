package com.oms.collector.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 모든 요청의 Authorization 헤더에서 companyCode를 추출해
 * TenantContext에 설정합니다. 라이브러리 없이 JWT 페이로드만 Base64 디코딩합니다.
 */
@Slf4j
@Component
public class TenantInterceptor implements HandlerInterceptor {

    private static final Pattern CODE_PATTERN =
        Pattern.compile("\"companyCode\"\\s*:\\s*\"([^\"]+)\"");

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        String auth = req.getHeader("Authorization");
        String schema = "public";
        if (auth != null && auth.startsWith("Bearer ")) {
            String companyCode = extractCompanyCode(auth.substring(7));
            schema = TenantContext.toSchema(companyCode);
        }
        TenantContext.setCurrentTenant(schema);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res,
                                Object handler, Exception ex) {
        TenantContext.clear();
    }

    private String extractCompanyCode(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return "C00";
            String payload = parts[1];
            int mod = payload.length() % 4;
            if (mod == 2) payload += "==";
            else if (mod == 3) payload += "=";
            String json = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
            Matcher m = CODE_PATTERN.matcher(json);
            return m.find() ? m.group(1) : "C00";
        } catch (Exception e) {
            return "C00";
        }
    }
}
