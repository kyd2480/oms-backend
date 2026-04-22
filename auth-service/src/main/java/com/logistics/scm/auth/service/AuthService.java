package com.logistics.scm.auth.service;

import com.logistics.scm.auth.dto.LoginRequest;
import com.logistics.scm.auth.dto.LoginResponse;
import com.logistics.scm.auth.dto.SignupRequest;
import com.logistics.scm.auth.dto.UserDTO;
import com.logistics.scm.auth.entity.User;
import com.logistics.scm.auth.repository.UserRepository;
import com.logistics.scm.auth.util.JwtTokenUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenUtil jwtTokenUtil;

    /** 회원가입 */
    @Transactional
    public LoginResponse signup(SignupRequest request) {
        if (userRepository.existsByUsername(request.getUsername()))
            return LoginResponse.failure("이미 사용 중인 아이디입니다");
        if (userRepository.existsByEmail(request.getEmail()))
            return LoginResponse.failure("이미 사용 중인 이메일입니다");

        String companyCode = (request.getCompanyCode() != null && !request.getCompanyCode().isBlank())
            ? request.getCompanyCode().toUpperCase() : "C00";

        User user = User.create(
            request.getUsername(),
            passwordEncoder.encode(request.getPassword()),
            request.getName(),
            request.getEmail(),
            User.UserRole.USER,
            companyCode
        );
        userRepository.save(user);

        String token = jwtTokenUtil.generateToken(user.getUsername(), user.getRole().name(), user.getCompanyCode());
        log.info("회원가입 완료: username={}", user.getUsername());
        return LoginResponse.success(token, UserDTO.from(user));
    }

    /** 로그인 */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        try {
            User user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new RuntimeException("아이디 또는 비밀번호가 올바르지 않습니다"));

            if (!user.isEnabled())
                return LoginResponse.failure("비활성화된 계정입니다");

            if (!passwordEncoder.matches(request.getPassword(), user.getPassword()))
                return LoginResponse.failure("아이디 또는 비밀번호가 올바르지 않습니다");

            user.updateLastLoginTime();
            userRepository.save(user);

            String token = jwtTokenUtil.generateToken(user.getUsername(), user.getRole().name(), user.getCompanyCode());
            log.info("로그인 성공: username={}, role={}", user.getUsername(), user.getRole());
            return LoginResponse.success(token, UserDTO.from(user));

        } catch (RuntimeException e) {
            log.error("로그인 실패: {}", e.getMessage());
            return LoginResponse.failure(e.getMessage());
        }
    }

    /** 관리자 회사 컨텍스트 전환 */
    @Transactional(readOnly = true)
    public LoginResponse switchAdminCompanyContext(String token, String companyCode) {
        try {
            String username = jwtTokenUtil.getUsernameFromToken(token);
            if (!jwtTokenUtil.validateToken(token, username)) {
                return LoginResponse.failure("유효하지 않은 토큰입니다");
            }

            String role = jwtTokenUtil.getRoleFromToken(token);
            if (!User.UserRole.ADMIN.name().equals(role)) {
                return LoginResponse.failure("관리자만 회사를 선택할 수 있습니다");
            }

            String normalizedCode = normalizeCompanyCode(companyCode);
            User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));
            if (!user.isEnabled()) {
                return LoginResponse.failure("비활성화된 계정입니다");
            }

            String switchedToken = jwtTokenUtil.generateToken(
                user.getUsername(),
                user.getRole().name(),
                normalizedCode
            );
            UserDTO switchedUser = UserDTO.from(user);
            switchedUser.setCompanyCode(normalizedCode);
            log.info("관리자 회사 컨텍스트 전환: username={}, companyCode={}", username, normalizedCode);
            return LoginResponse.success(switchedToken, switchedUser);
        } catch (RuntimeException e) {
            log.error("관리자 회사 컨텍스트 전환 실패: {}", e.getMessage());
            return LoginResponse.failure(e.getMessage());
        }
    }

    public boolean validateToken(String token, String username) {
        return jwtTokenUtil.validateToken(token, username);
    }

    public String getUsernameFromToken(String token) {
        return jwtTokenUtil.getUsernameFromToken(token);
    }

    public String getRoleFromToken(String token) {
        return jwtTokenUtil.getRoleFromToken(token);
    }

    public String getCompanyCodeFromToken(String token) {
        return jwtTokenUtil.getCompanyCodeFromToken(token);
    }

    /** 모든 사용자 목록 (관리자용) */
    @Transactional(readOnly = true)
    public java.util.List<UserDTO> listAllUsers() {
        return userRepository.findAll().stream().map(UserDTO::from).toList();
    }

    /** 회사 코드 변경 (관리자용) */
    @Transactional
    public UserDTO updateCompanyCode(java.util.UUID userId, String companyCode) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));
        user.setCompanyCode(companyCode.toUpperCase());
        return UserDTO.from(userRepository.save(user));
    }

    /** 페이지 권한 변경 (관리자용) */
    @Transactional
    public UserDTO updatePagePermissions(java.util.UUID userId, java.util.List<String> pages) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));
        // null 또는 빈 리스트 → 전체 허용
        if (pages == null || pages.isEmpty()) {
            user.setPagePermissions(null);
        } else {
            user.setPagePermissions(String.join(",", pages));
        }
        return UserDTO.from(userRepository.save(user));
    }

    private String normalizeCompanyCode(String companyCode) {
        String code = companyCode == null ? "C00" : companyCode.trim().toUpperCase();
        if (code.isBlank()) {
            code = "C00";
        }
        if (!code.matches("[A-Z0-9_]{1,20}")) {
            throw new RuntimeException("유효하지 않은 회사코드입니다");
        }
        return code;
    }
}
