package com.oms.collector.config;

/**
 * 현재 요청의 테넌트(회사 코드 → 스키마명)를 ThreadLocal로 보관합니다.
 * C00 → "public", 나머지 → 소문자 변환 (e.g. C01 → "c01")
 */
public class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    public static void setCurrentTenant(String schema) { CURRENT.set(schema); }
    public static String getCurrentTenant()             { return CURRENT.get(); }
    public static void clear()                          { CURRENT.remove(); }

    /** 회사코드(C00, C01…)를 PostgreSQL 스키마명으로 변환 */
    public static String toSchema(String companyCode) {
        if (companyCode == null || companyCode.isBlank() || "C00".equalsIgnoreCase(companyCode))
            return "public";
        return companyCode.toLowerCase().replaceAll("[^a-z0-9_]", "");
    }
}
