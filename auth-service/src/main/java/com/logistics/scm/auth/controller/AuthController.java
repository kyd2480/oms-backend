package com.logistics.scm.auth.controller;

import com.logistics.scm.auth.dto.LoginRequest;
import com.logistics.scm.auth.dto.LoginResponse;
import com.logistics.scm.auth.dto.ChangePasswordRequest;
import com.logistics.scm.auth.dto.ResetPasswordRequest;
import com.logistics.scm.auth.dto.SignupRequest;
import com.logistics.scm.auth.dto.UserDTO;
import com.logistics.scm.auth.dto.VerificationConfirmRequest;
import com.logistics.scm.auth.dto.VerificationConfirmResponse;
import com.logistics.scm.auth.dto.VerificationSendRequest;
import com.logistics.scm.auth.dto.VerificationSendResponse;
import com.logistics.scm.auth.service.AuthService;
import com.logistics.scm.auth.service.MaintenanceService;
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
    private final MaintenanceService maintenanceService;

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

    @PostMapping("/signup/send-code")
    public ResponseEntity<VerificationSendResponse> sendSignupCode(@RequestBody VerificationSendRequest request) {
        VerificationSendResponse response = authService.sendSignupVerificationCode(request.getMethod(), request.getEmail(), request.getPhone());
        return response.isSuccess() ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
    }

    @PostMapping("/signup/verify-code")
    public ResponseEntity<VerificationConfirmResponse> verifySignupCode(@RequestBody VerificationConfirmRequest request) {
        VerificationConfirmResponse response = authService.confirmSignupVerificationCode(request);
        return response.isSuccess() ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
    }

    @PostMapping("/find-id/send-code")
    public ResponseEntity<VerificationSendResponse> sendFindIdCode(@RequestBody VerificationSendRequest request) {
        VerificationSendResponse response = authService.sendFindIdVerificationCode(request.getMethod(), request.getEmail(), request.getPhone());
        return response.isSuccess() ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
    }

    @PostMapping("/find-id/verify-code")
    public ResponseEntity<VerificationConfirmResponse> findId(@RequestBody VerificationConfirmRequest request) {
        VerificationConfirmResponse response = authService.findUsername(request);
        return response.isSuccess() ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
    }

    @PostMapping("/password-reset/send-code")
    public ResponseEntity<VerificationSendResponse> sendResetPasswordCode(@RequestBody VerificationSendRequest request) {
        VerificationSendResponse response = authService.sendResetPasswordVerificationCode(
            request.getMethod(), request.getUsername(), request.getEmail(), request.getPhone()
        );
        return response.isSuccess() ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<VerificationConfirmResponse> resetPassword(@RequestBody ResetPasswordRequest request) {
        VerificationConfirmResponse response = authService.resetPassword(request);
        return response.isSuccess() ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
    }

    @PostMapping("/change-password")
    public ResponseEntity<VerificationConfirmResponse> changePassword(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody ChangePasswordRequest request) {
        String token = authHeader.replace("Bearer ", "");
        VerificationConfirmResponse response = authService.changePassword(token, request);
        return response.isSuccess() ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
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

    /** 점검 설정 공개 조회 — GET /api/auth/maintenance/public */
    @GetMapping("/maintenance/public")
    public ResponseEntity<?> getPublicMaintenance() {
        return ResponseEntity.ok(maintenanceService.getSettings());
    }

    /** 점검 설정 관리자 조회 — GET /api/auth/maintenance */
    @GetMapping("/maintenance")
    public ResponseEntity<?> getMaintenance() {
        return ResponseEntity.ok(maintenanceService.getSettings());
    }

    /** 점검 설정 저장 — PUT /api/auth/maintenance */
    @PutMapping("/maintenance")
    public ResponseEntity<?> updateMaintenance(@RequestBody Map<String, Object> body) {
        try {
            Boolean enabled = (Boolean) body.get("enabled");
            String startAtRaw = body.get("startAt") == null ? null : String.valueOf(body.get("startAt"));
            String endAtRaw = body.get("endAt") == null ? null : String.valueOf(body.get("endAt"));
            String title = body.get("title") == null ? null : String.valueOf(body.get("title"));
            String message = body.get("message") == null ? null : String.valueOf(body.get("message"));
            java.time.LocalDateTime startAt = (startAtRaw == null || startAtRaw.isBlank()) ? null : java.time.LocalDateTime.parse(startAtRaw);
            java.time.LocalDateTime endAt = (endAtRaw == null || endAtRaw.isBlank()) ? null : java.time.LocalDateTime.parse(endAtRaw);
            return ResponseEntity.ok(maintenanceService.updateSettings(enabled, startAt, endAt, title, message));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
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

    /** 만료일 변경 — PUT /api/auth/users/{userId}/account-dates (관리자용) */
    @PutMapping("/users/{userId}/account-dates")
    public ResponseEntity<?> updateAccountDates(
            @PathVariable UUID userId,
            @RequestBody Map<String, String> body) {
        try {
            String expiresAtRaw = body.get("expiresAt");
            java.time.LocalDate expiresAt = (expiresAtRaw == null || expiresAtRaw.isBlank()) ? null : java.time.LocalDate.parse(expiresAtRaw);
            return ResponseEntity.ok(authService.updateAccountDates(userId, expiresAt));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** 사용자 삭제 — DELETE /api/auth/users/{userId} (관리자용) */
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> deleteUser(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID userId) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String username = authService.getUsernameFromToken(token);
            UserDTO requester = authService.listAllUsers().stream()
                .filter(item -> item.getUsername().equals(username))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("요청 사용자를 찾을 수 없습니다"));
            authService.deleteUser(requester.getUserId(), userId);
            return ResponseEntity.ok(Map.of("message", "사용자가 삭제되었습니다"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
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
