package com.oms.collector.controller;

import com.oms.collector.config.TenantContext;
import com.oms.collector.service.TenantSchemaInitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 테넌트(회사 스키마) 관리 API
 *
 * GET  /api/tenant/status          현재 테넌트 스키마 상태 조회
 * POST /api/tenant/init            현재 테넌트 스키마 초기화
 * GET  /api/tenant/list            전체 테넌트 스키마 목록 (관리자용)
 * POST /api/tenant/create/{code}   새 회사 스키마 생성 (관리자용)
 */
@Slf4j
@RestController
@RequestMapping("/api/tenant")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TenantController {

    private final TenantSchemaInitService initService;

    /** 현재 테넌트(로그인 회사)의 스키마 상태 반환 */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        String schema = TenantContext.getCurrentTenant();
        if (schema == null) schema = "public";
        boolean exists  = initService.schemaExists(schema);
        boolean hasData = exists && initService.hasData(schema);
        return ResponseEntity.ok(Map.of(
            "schema",  schema,
            "exists",  exists,
            "hasData", hasData
        ));
    }

    /** 현재 테넌트 스키마 초기화 (빈 스키마면 테이블 생성) */
    @PostMapping("/init")
    public ResponseEntity<Map<String, Object>> init() {
        String schema = TenantContext.getCurrentTenant();
        if (schema == null || schema.equals("public")) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "기본 회사(C00/public)는 초기화 대상이 아닙니다"
            ));
        }
        try {
            initService.initSchema(schema);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "schema",  schema,
                "message", "스키마 초기화 완료: " + schema
            ));
        } catch (Exception e) {
            log.error("[TenantInit] 초기화 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "초기화 실패: " + e.getMessage()
            ));
        }
    }

    /** 전체 테넌트 스키마 목록 */
    @GetMapping("/list")
    public ResponseEntity<List<String>> list() {
        return ResponseEntity.ok(initService.listTenantSchemas());
    }

    /** 관리자가 새 회사 코드의 스키마를 직접 생성 */
    @PostMapping("/create/{companyCode}")
    public ResponseEntity<Map<String, Object>> create(@PathVariable String companyCode) {
        String schema = TenantContext.toSchema(companyCode);
        if (schema.equals("public")) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "C00은 기본 스키마(public)입니다. 생성이 필요하지 않습니다."
            ));
        }
        try {
            initService.initSchema(schema);
            return ResponseEntity.ok(Map.of(
                "success",     true,
                "companyCode", companyCode.toUpperCase(),
                "schema",      schema,
                "message",     "스키마 생성 완료: " + schema
            ));
        } catch (Exception e) {
            log.error("[TenantInit] 스키마 생성 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "스키마 생성 실패: " + e.getMessage()
            ));
        }
    }
}
