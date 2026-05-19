package com.logistics.scm.auth.service;

import com.logistics.scm.auth.dto.LoginRequest;
import com.logistics.scm.auth.dto.LoginResponse;
import com.logistics.scm.auth.dto.ChangePasswordRequest;
import com.logistics.scm.auth.dto.ResetPasswordRequest;
import com.logistics.scm.auth.dto.SignupRequest;
import com.logistics.scm.auth.dto.UserDTO;
import com.logistics.scm.auth.dto.VerificationConfirmRequest;
import com.logistics.scm.auth.dto.VerificationConfirmResponse;
import com.logistics.scm.auth.dto.VerificationSendResponse;
import com.logistics.scm.auth.entity.User;
import com.logistics.scm.auth.entity.VerificationCode;
import com.logistics.scm.auth.repository.UserRepository;
import com.logistics.scm.auth.util.JwtTokenUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private static final long PASSWORD_EXPIRE_DAYS = 90;
    private static final java.util.List<String> DEFAULT_SIGNUP_PAGE_PERMISSIONS = java.util.List.of(
        "orders.orderinput",
        "orders.allocate",
        "orders.nameMatch",
        "orders.dupcheck",
        "orders.bundle",
        "orders.stockMatch",
        "orders.invoice",
        "orders.inspectShip",
        "orders.scanErrorCheck",
        "orders.marketShip",
        "cs.management",
        "delivery.track",
        "cancel.management",
        "inventory.product.list",
        "inventory.io.list",
        "inventory.io.in",
        "inventory.io.out",
        "inventory.barcode.product",
        "inventory.warehouse.moveStock"
    );

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenUtil jwtTokenUtil;
    private final VerificationCodeService verificationCodeService;
    private final MaintenanceService maintenanceService;

    /** 회원가입 */
    @Transactional
    public LoginResponse signup(SignupRequest request) {
        try {
            if (userRepository.existsByUsername(request.getUsername()))
                return LoginResponse.failure("이미 사용 중인 아이디입니다");
            String email = normalizeEmail(request.getEmail());
            String phone = normalizePhone(request.getPhone());
            if (email == null && phone == null) {
                return LoginResponse.failure("이메일 또는 연락처를 하나 이상 입력하세요.");
            }
            if (email != null && userRepository.existsByEmail(email))
                return LoginResponse.failure("이미 사용 중인 이메일입니다");
            if (phone != null && userRepository.existsByPhone(phone))
                return LoginResponse.failure("이미 사용 중인 연락처입니다");

            String companyCode = (request.getCompanyCode() != null && !request.getCompanyCode().isBlank())
                ? request.getCompanyCode().toUpperCase() : "C00";
            VerificationCode.Method method = verificationCodeService.parseMethod(request.getVerificationMethod());
            verificationCodeService.consumeSignupVerification(request.getVerificationToken(), method, email, phone);

            boolean emailVerified = method == VerificationCode.Method.EMAIL && email != null;
            boolean phoneVerified = method == VerificationCode.Method.PHONE && phone != null;

            User user = User.create(
                request.getUsername(),
                passwordEncoder.encode(request.getPassword()),
                request.getName(),
                email,
                phone,
                emailVerified,
                phoneVerified,
                User.UserRole.USER,
                companyCode
            );
            user.setPagePermissions(String.join(",", DEFAULT_SIGNUP_PAGE_PERMISSIONS));
            userRepository.save(user);

            String token = jwtTokenUtil.generateToken(user.getUsername(), user.getRole().name(), user.getCompanyCode());
            boolean passwordExpired = isPasswordExpired(user);
            log.info("회원가입 완료: username={}", user.getUsername());
            return LoginResponse.success(token, UserDTO.from(user, passwordExpired));
        } catch (RuntimeException e) {
            return LoginResponse.failure(e.getMessage());
        }
    }

    /** 로그인 */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        try {
            User user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new RuntimeException("아이디 또는 비밀번호가 올바르지 않습니다"));

            if (!user.isEnabled())
                return LoginResponse.failure("비활성화된 계정입니다");

            if (maintenanceService.isMaintenanceActive() && user.getRole() != User.UserRole.ADMIN)
                return LoginResponse.failure("현재 시스템 점검 중입니다. 관리자만 로그인할 수 있습니다.");

            if (isAccountExpired(user))
                return LoginResponse.failure("계정 사용기한이 만료되었습니다. 관리자에게 문의하세요.");

            if (!passwordEncoder.matches(request.getPassword(), user.getPassword()))
                return LoginResponse.failure("아이디 또는 비밀번호가 올바르지 않습니다");

            user.updateLastLoginTime();
            userRepository.save(user);

            String token = jwtTokenUtil.generateToken(user.getUsername(), user.getRole().name(), user.getCompanyCode());
            boolean passwordExpired = isPasswordExpired(user);
            log.info("로그인 성공: username={}, role={}", user.getUsername(), user.getRole());
            return LoginResponse.success(token, UserDTO.from(user, passwordExpired));

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
            if (maintenanceService.isMaintenanceActive() && user.getRole() != User.UserRole.ADMIN) {
                return LoginResponse.failure("현재 시스템 점검 중입니다. 관리자만 로그인할 수 있습니다.");
            }
            if (isAccountExpired(user)) {
                return LoginResponse.failure("계정 사용기한이 만료되었습니다. 관리자에게 문의하세요.");
            }

            String switchedToken = jwtTokenUtil.generateToken(
                user.getUsername(),
                user.getRole().name(),
                normalizedCode
            );
            UserDTO switchedUser = UserDTO.from(user, isPasswordExpired(user));
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

    /** 만료일 변경 (관리자용) */
    @Transactional
    public UserDTO updateAccountDates(java.util.UUID userId, LocalDate expiresAt) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));
        LocalDate joinedAt = user.getJoinedAt() != null ? user.getJoinedAt() : LocalDate.now();
        if (expiresAt != null && expiresAt.isBefore(joinedAt)) {
            throw new RuntimeException("만료날짜는 가입날짜보다 빠를 수 없습니다.");
        }
        user.setExpiresAt(expiresAt);
        return UserDTO.from(userRepository.save(user));
    }

    /** 사용자 삭제 (관리자용) */
    @Transactional
    public void deleteUser(java.util.UUID requesterUserId, java.util.UUID targetUserId) {
        if (requesterUserId != null && requesterUserId.equals(targetUserId)) {
            throw new RuntimeException("현재 로그인한 계정은 삭제할 수 없습니다.");
        }
        User target = userRepository.findById(targetUserId)
            .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));
        userRepository.delete(target);
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

    public String encodePassword(String password) {
        return passwordEncoder.encode(password);
    }

    public VerificationSendResponse sendSignupVerificationCode(String method, String email, String phone) {
        try {
            return verificationCodeService.sendSignupCode(verificationCodeService.parseMethod(method), email, phone);
        } catch (RuntimeException e) {
            return VerificationSendResponse.failure(e.getMessage());
        }
    }

    public VerificationConfirmResponse confirmSignupVerificationCode(VerificationConfirmRequest request) {
        try {
            return verificationCodeService.confirmSignupCode(request);
        } catch (RuntimeException e) {
            return VerificationConfirmResponse.failure(e.getMessage());
        }
    }

    public VerificationSendResponse sendFindIdVerificationCode(String method, String email, String phone) {
        try {
            return verificationCodeService.sendFindIdCode(verificationCodeService.parseMethod(method), email, phone);
        } catch (RuntimeException e) {
            return VerificationSendResponse.failure(e.getMessage());
        }
    }

    public VerificationConfirmResponse findUsername(VerificationConfirmRequest request) {
        try {
            return verificationCodeService.confirmFindIdCode(request);
        } catch (RuntimeException e) {
            return VerificationConfirmResponse.failure(e.getMessage());
        }
    }

    public VerificationSendResponse sendResetPasswordVerificationCode(String method, String username, String email, String phone) {
        try {
            return verificationCodeService.sendResetPasswordCode(verificationCodeService.parseMethod(method), username, email, phone);
        } catch (RuntimeException e) {
            return VerificationSendResponse.failure(e.getMessage());
        }
    }

    public VerificationConfirmResponse resetPassword(ResetPasswordRequest request) {
        VerificationConfirmRequest confirmRequest = new VerificationConfirmRequest();
        confirmRequest.setMethod(request.getMethod());
        confirmRequest.setUsername(request.getUsername());
        confirmRequest.setEmail(request.getEmail());
        confirmRequest.setPhone(request.getPhone());
        confirmRequest.setCode(request.getCode());
        try {
            return verificationCodeService.resetPassword(confirmRequest, request.getNewPassword(), this);
        } catch (RuntimeException e) {
            return VerificationConfirmResponse.failure(e.getMessage());
        }
    }

    @Transactional
    public VerificationConfirmResponse changePassword(String token, ChangePasswordRequest request) {
        try {
            String username = jwtTokenUtil.getUsernameFromToken(token);
            User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
            if (request.getCurrentPassword() == null || !passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                return VerificationConfirmResponse.failure("현재 비밀번호가 올바르지 않습니다.");
            }
            if (request.getNewPassword() == null || request.getNewPassword().length() < 6) {
                return VerificationConfirmResponse.failure("새 비밀번호는 6자 이상이어야 합니다.");
            }
            if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
                return VerificationConfirmResponse.failure("현재 비밀번호와 다른 비밀번호를 입력하세요.");
            }
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
            user.setPasswordChangedAt(java.time.LocalDateTime.now());
            userRepository.save(user);
            return VerificationConfirmResponse.success("비밀번호가 변경되었습니다.", null);
        } catch (RuntimeException e) {
            return VerificationConfirmResponse.failure(e.getMessage());
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String value = email.trim().toLowerCase();
        if (!value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new RuntimeException("올바른 이메일 형식이 아닙니다.");
        }
        return value;
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() < 10 || digits.length() > 11) {
            throw new RuntimeException("올바른 연락처 형식이 아닙니다.");
        }
        return digits;
    }

    private boolean isPasswordExpired(User user) {
        if (user.getRole() == User.UserRole.ADMIN) {
            return false;
        }
        java.time.LocalDateTime base = user.getPasswordChangedAt() != null
            ? user.getPasswordChangedAt()
            : (user.getCreatedAt() != null ? user.getCreatedAt() : java.time.LocalDateTime.now());
        return base.plusDays(PASSWORD_EXPIRE_DAYS).isBefore(java.time.LocalDateTime.now());
    }

    private boolean isAccountExpired(User user) {
        return user.getExpiresAt() != null && !user.getExpiresAt().isAfter(LocalDate.now());
    }
}
