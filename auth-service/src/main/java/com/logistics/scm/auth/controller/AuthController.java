package com.logistics.scm.auth.controller;

import com.logistics.scm.auth.dto.LoginRequest;
import com.logistics.scm.auth.dto.LoginResponse;
import com.logistics.scm.auth.dto.SignupRequest;
import com.logistics.scm.auth.dto.UserDTO;
import com.logistics.scm.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    /** 회원가입 — POST /api/auth/signup */
    @PostMapping("/signup")
    public ResponseEntity<LoginResponse> signup(@Valid @RequestBody SignupRequest request) {
        log.info("회원가입 요청: username={}", request.getUsername());
        LoginResponse response = authService.signup(request);
        return response.isSuccess()
            ? ResponseEntity.ok(response)
            : ResponseEntity.badRequest().body(response);
    }

    /** 로그인 — POST /api/auth/login */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("로그인 요청: username={}", request.getUsername());
        LoginResponse response = authService.login(request);
        return response.isSuccess()
            ? ResponseEntity.ok(response)
            : ResponseEntity.badRequest().body(response);
    }

    /** 관리자 회사 컨텍스트 선택 — POST /api/auth/admin/company-context */
    @PostMapping("/admin/company-context")
    public ResponseEntity<LoginResponse> switchAdminCompanyContext(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {
        String companyCode = body.get("companyCode");
        if (companyCode == null || companyCode.isBlank()) {
            return ResponseEntity.badRequest().body(LoginResponse.failure("companyCode 필드가 필요합니다"));
        }

        String token = authHeader.replace("Bearer ", "");
        LoginResponse response = authService.switchAdminCompanyContext(token, companyCode);
        return response.isSuccess()
            ? ResponseEntity.ok(response)
            : ResponseEntity.badRequest().body(response);
    }

    /** 토큰 검증 — GET /api/auth/validate */
    @GetMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestHeader("Authorization") String authHeader) {
        try {
            String token    = authHeader.replace("Bearer ", "");
            String username = authService.getUsernameFromToken(token);
            boolean isValid = authService.validateToken(token, username);
            return isValid
                ? ResponseEntity.ok(new ValidationResponse(true, "유효한 토큰입니다", username))
                : ResponseEntity.badRequest().body(new ValidationResponse(false, "유효하지 않은 토큰입니다", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ValidationResponse(false, "토큰 검증 실패", null));
        }
    }

    /** 현재 사용자 — GET /api/auth/me */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestHeader("Authorization") String authHeader) {
        try {
            String token    = authHeader.replace("Bearer ", "");
            String username = authService.getUsernameFromToken(token);
            String role     = authService.getRoleFromToken(token);
            return ResponseEntity.ok(new CurrentUserResponse(username, role));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("사용자 정보 조회 실패");
        }
    }

    /** 전체 사용자 목록 — GET /api/auth/users (관리자용) */
    @GetMapping("/users")
    public ResponseEntity<List<UserDTO>> listUsers() {
        return ResponseEntity.ok(authService.listAllUsers());
    }

    /** 페이지 권한 변경 — PUT /api/auth/users/{userId}/permissions (관리자용) */
    @PutMapping("/users/{userId}/permissions")
    public ResponseEntity<?> updatePermissions(
            @PathVariable UUID userId,
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        java.util.List<String> pages = (java.util.List<String>) body.get("pages");
        return ResponseEntity.ok(authService.updatePagePermissions(userId, pages));
    }

    /** 회사 코드 변경 — PUT /api/auth/users/{userId}/company-code (관리자용) */
    @PutMapping("/users/{userId}/company-code")
    public ResponseEntity<?> updateCompanyCode(
            @PathVariable UUID userId,
            @RequestBody Map<String, String> body) {
        String code = body.get("companyCode");
        if (code == null || code.isBlank())
            return ResponseEntity.badRequest().body(Map.of("message", "companyCode 필드가 필요합니다"));
        return ResponseEntity.ok(authService.updateCompanyCode(userId, code));
    }

    /** 현재 토큰의 회사 코드 반환 — GET /api/auth/company-code */
    @GetMapping("/company-code")
    public ResponseEntity<?> getCompanyCode(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String code  = authService.getCompanyCodeFromToken(token);
            return ResponseEntity.ok(Map.of("companyCode", code));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "토큰 파싱 실패"));
        }
    }

    record ValidationResponse(boolean valid, String message, String username) {}
    record CurrentUserResponse(String username, String role) {}
}
