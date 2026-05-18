package com.logistics.scm.auth.service;

import com.logistics.scm.auth.dto.VerificationConfirmRequest;
import com.logistics.scm.auth.dto.VerificationConfirmResponse;
import com.logistics.scm.auth.dto.VerificationSendResponse;
import com.logistics.scm.auth.entity.User;
import com.logistics.scm.auth.entity.VerificationCode;
import com.logistics.scm.auth.repository.UserRepository;
import com.logistics.scm.auth.repository.VerificationCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationCodeService {

    private final VerificationCodeRepository verificationCodeRepository;
    private final UserRepository userRepository;
    private final JavaMailSender javaMailSender;

    @Value("${auth.verification.code-expire-minutes:10}")
    private int expireMinutes;

    @Value("${auth.verification.mail-from:no-reply@saerowms.local}")
    private String mailFrom;

    @Value("${auth.verification.dev-expose-code:true}")
    private boolean devExposeCode;

    private final SecureRandom random = new SecureRandom();

    @Transactional
    public VerificationSendResponse sendSignupCode(VerificationCode.Method method, String email, String phone) {
        String target = resolveTarget(method, email, phone);
        validateSignupTargetAvailability(method, target);
        VerificationCode code = createFreshCode(VerificationCode.Purpose.SIGNUP, method, target, null);
        return deliverCode(code);
    }

    @Transactional
    public VerificationConfirmResponse confirmSignupCode(VerificationConfirmRequest request) {
        VerificationCode.Method method = parseMethod(request.getMethod());
        String target = resolveTarget(method, request.getEmail(), request.getPhone());
        VerificationCode item = loadLatestCode(VerificationCode.Purpose.SIGNUP, method, target, null);
        verifyCode(item, request.getCode());
        String token = UUID.randomUUID().toString().replace("-", "");
        item.markVerified(token);
        verificationCodeRepository.save(item);
        return VerificationConfirmResponse.success("인증이 완료되었습니다.", token);
    }

    @Transactional
    public VerificationSendResponse sendFindIdCode(VerificationCode.Method method, String email, String phone) {
        String target = resolveTarget(method, email, phone);
        User user = findUserByMethodTarget(method, target);
        VerificationCode code = createFreshCode(VerificationCode.Purpose.FIND_ID, method, target, user.getUsername());
        return deliverCode(code);
    }

    @Transactional
    public VerificationConfirmResponse confirmFindIdCode(VerificationConfirmRequest request) {
        VerificationCode.Method method = parseMethod(request.getMethod());
        String target = resolveTarget(method, request.getEmail(), request.getPhone());
        User user = findUserByMethodTarget(method, target);
        VerificationCode item = loadLatestCode(VerificationCode.Purpose.FIND_ID, method, target, user.getUsername());
        verifyCode(item, request.getCode());
        item.markUsed();
        verificationCodeRepository.save(item);
        return VerificationConfirmResponse.foundUser("아이디를 찾았습니다.", user.getUsername(), user.getName());
    }

    @Transactional
    public VerificationSendResponse sendResetPasswordCode(VerificationCode.Method method, String username, String email, String phone) {
        String normalizedUsername = normalizeUsername(username);
        String target = resolveTarget(method, email, phone);
        User user = findUserForPasswordReset(normalizedUsername, method, target);
        VerificationCode code = createFreshCode(VerificationCode.Purpose.RESET_PASSWORD, method, target, user.getUsername());
        return deliverCode(code);
    }

    @Transactional
    public VerificationConfirmResponse resetPassword(VerificationConfirmRequest request, String newPassword, AuthService authService) {
        if (newPassword == null || newPassword.length() < 6) {
            return VerificationConfirmResponse.failure("비밀번호는 6자 이상이어야 합니다.");
        }
        VerificationCode.Method method = parseMethod(request.getMethod());
        String username = normalizeUsername(request.getUsername());
        String target = resolveTarget(method, request.getEmail(), request.getPhone());
        User user = findUserForPasswordReset(username, method, target);
        VerificationCode item = loadLatestCode(VerificationCode.Purpose.RESET_PASSWORD, method, target, user.getUsername());
        verifyCode(item, request.getCode());
        item.markUsed();
        verificationCodeRepository.save(item);
        user.setPassword(authService.encodePassword(newPassword));
        userRepository.save(user);
        return VerificationConfirmResponse.success("비밀번호가 재설정되었습니다.", null);
    }

    @Transactional
    public void consumeSignupVerification(String verificationToken, VerificationCode.Method method, String email, String phone) {
        String target = resolveTarget(method, email, phone);
        VerificationCode item = verificationCodeRepository
            .findTopByPurposeAndMethodAndTargetValueAndVerificationTokenAndVerifiedTrueAndUsedFalseOrderByCreatedAtDesc(
                VerificationCode.Purpose.SIGNUP, method, target, verificationToken
            )
            .orElseThrow(() -> new RuntimeException("인증이 완료되지 않았습니다."));
        if (item.isExpired()) {
            throw new RuntimeException("인증이 만료되었습니다. 다시 인증해주세요.");
        }
        item.markUsed();
        verificationCodeRepository.save(item);
    }

    public VerificationCode.Method parseMethod(String rawMethod) {
        if (rawMethod == null || rawMethod.isBlank()) {
            throw new RuntimeException("인증 수단을 선택하세요.");
        }
        try {
            return VerificationCode.Method.valueOf(rawMethod.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("지원하지 않는 인증 수단입니다.");
        }
    }

    private VerificationCode createFreshCode(VerificationCode.Purpose purpose, VerificationCode.Method method, String target, String username) {
        invalidateOldCodes(purpose, method, target, username);
        String codeValue = "%06d".formatted(random.nextInt(1_000_000));
        VerificationCode item = VerificationCode.create(purpose, method, target, username, codeValue, expireMinutes);
        return verificationCodeRepository.save(item);
    }

    private void invalidateOldCodes(VerificationCode.Purpose purpose, VerificationCode.Method method, String target, String username) {
        List<VerificationCode> oldCodes = username == null
            ? verificationCodeRepository.findByPurposeAndMethodAndTargetValueAndUsedFalse(purpose, method, target)
            : verificationCodeRepository.findByPurposeAndMethodAndTargetValueAndUsernameAndUsedFalse(purpose, method, target, username);
        for (VerificationCode old : oldCodes) {
            old.markUsed();
        }
        verificationCodeRepository.saveAll(oldCodes);
    }

    private VerificationSendResponse deliverCode(VerificationCode code) {
        String masked = maskTarget(code.getMethod(), code.getTargetValue());
        if (code.getMethod() == VerificationCode.Method.EMAIL) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(mailFrom);
                message.setTo(code.getTargetValue());
                message.setSubject(buildMailSubject(code.getPurpose()));
                message.setText(buildMailBody(code));
                javaMailSender.send(message);
                return VerificationSendResponse.success(masked + " 로 인증번호를 발송했습니다.", "EMAIL", devExposeCode ? code.getVerificationCode() : null);
            } catch (Exception e) {
                log.warn("이메일 인증 발송 실패, mock으로 대체합니다. target={}, reason={}", code.getTargetValue(), e.getMessage());
                return VerificationSendResponse.success(masked + " 인증번호 발송 설정이 없어 개발코드로 대체되었습니다.", "MOCK", code.getVerificationCode());
            }
        }
        log.info("휴대폰 인증코드 발급: target={}, code={}", code.getTargetValue(), code.getVerificationCode());
        return VerificationSendResponse.success(masked + " 로 인증번호를 발급했습니다.", "MOCK", code.getVerificationCode());
    }

    private VerificationCode loadLatestCode(VerificationCode.Purpose purpose, VerificationCode.Method method, String target, String username) {
        return (username == null
            ? verificationCodeRepository.findTopByPurposeAndMethodAndTargetValueAndUsedFalseOrderByCreatedAtDesc(purpose, method, target)
            : verificationCodeRepository.findTopByPurposeAndMethodAndTargetValueAndUsernameAndUsedFalseOrderByCreatedAtDesc(purpose, method, target, username))
            .orElseThrow(() -> new RuntimeException("먼저 인증번호를 발송하세요."));
    }

    private void verifyCode(VerificationCode item, String code) {
        if (item.isExpired()) {
            item.markUsed();
            verificationCodeRepository.save(item);
            throw new RuntimeException("인증번호가 만료되었습니다.");
        }
        if (code == null || !item.getVerificationCode().equals(code.trim())) {
            throw new RuntimeException("인증번호가 올바르지 않습니다.");
        }
    }

    private void validateSignupTargetAvailability(VerificationCode.Method method, String target) {
        if (method == VerificationCode.Method.EMAIL && userRepository.existsByEmail(target)) {
            throw new RuntimeException("이미 사용 중인 이메일입니다.");
        }
        if (method == VerificationCode.Method.PHONE && userRepository.existsByPhone(target)) {
            throw new RuntimeException("이미 사용 중인 연락처입니다.");
        }
    }

    private User findUserByMethodTarget(VerificationCode.Method method, String target) {
        return switch (method) {
            case EMAIL -> userRepository.findByEmail(target)
                .orElseThrow(() -> new RuntimeException("해당 이메일로 가입된 계정을 찾을 수 없습니다."));
            case PHONE -> userRepository.findByPhone(target)
                .orElseThrow(() -> new RuntimeException("해당 연락처로 가입된 계정을 찾을 수 없습니다."));
        };
    }

    private User findUserForPasswordReset(String username, VerificationCode.Method method, String target) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("해당 아이디를 찾을 수 없습니다."));
        boolean matched = switch (method) {
            case EMAIL -> target.equalsIgnoreCase(nvl(user.getEmail()));
            case PHONE -> target.equals(normalizePhone(user.getPhone()));
        };
        if (!matched) {
            throw new RuntimeException("입력한 아이디와 인증 정보가 일치하지 않습니다.");
        }
        return user;
    }

    private String resolveTarget(VerificationCode.Method method, String email, String phone) {
        return switch (method) {
            case EMAIL -> normalizeEmail(email);
            case PHONE -> normalizePhone(phone);
        };
    }

    private String normalizeEmail(String email) {
        String value = nvl(email).trim().toLowerCase();
        if (value.isBlank() || !value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new RuntimeException("올바른 이메일을 입력하세요.");
        }
        return value;
    }

    private String normalizePhone(String phone) {
        String digits = nvl(phone).replaceAll("[^0-9]", "");
        if (digits.length() < 10 || digits.length() > 11) {
            throw new RuntimeException("올바른 연락처를 입력하세요.");
        }
        return digits;
    }

    private String normalizeUsername(String username) {
        String value = nvl(username).trim();
        if (value.isBlank()) {
            throw new RuntimeException("아이디를 입력하세요.");
        }
        return value;
    }

    private String maskTarget(VerificationCode.Method method, String target) {
        if (method == VerificationCode.Method.EMAIL) {
            int idx = target.indexOf("@");
            if (idx <= 1) return target;
            return target.substring(0, 2) + "***" + target.substring(idx);
        }
        if (target.length() < 7) return target;
        return target.substring(0, 3) + "****" + target.substring(target.length() - 4);
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

    private String buildMailSubject(VerificationCode.Purpose purpose) {
        return switch (purpose) {
            case SIGNUP -> "[새로WMS] 회원가입 인증번호 안내";
            case FIND_ID -> "[새로WMS] 아이디 찾기 인증번호 안내";
            case RESET_PASSWORD -> "[새로WMS] 비밀번호 재설정 인증번호 안내";
        };
    }

    private String buildMailBody(VerificationCode code) {
        String intro = switch (code.getPurpose()) {
            case SIGNUP -> "새로WMS 회원가입 인증을 요청하셨습니다.";
            case FIND_ID -> "새로WMS 아이디 찾기 인증을 요청하셨습니다.";
            case RESET_PASSWORD -> "새로WMS 비밀번호 재설정 인증을 요청하셨습니다.";
        };

        return """
            %s

            아래 인증번호를 %d분 이내에 입력해주세요.

            인증번호: %s

            본인이 요청하지 않았다면 이 메일을 무시해주세요.

            감사합니다.
            새로WMS
            """.formatted(intro, expireMinutes, code.getVerificationCode());
    }
}
