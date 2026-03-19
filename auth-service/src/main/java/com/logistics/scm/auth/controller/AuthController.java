package com.logistics.scm.auth.controller;

import com.logistics.scm.auth.dto.LoginRequest;
import com.logistics.scm.auth.dto.LoginResponse;
import com.logistics.scm.auth.dto.SignupRequest;
import com.logistics.scm.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    record ValidationResponse(boolean valid, String message, String username) {}
    record CurrentUserResponse(String username, String role) {}
}
