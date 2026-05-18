package com.logistics.scm.auth.service;

import com.logistics.scm.auth.dto.VerificationConfirmRequest;
import com.logistics.scm.auth.dto.VerificationConfirmResponse;
import com.logistics.scm.auth.dto.VerificationSendResponse;
import com.logistics.scm.auth.entity.User;
import com.logistics.scm.auth.entity.VerificationCode;
import com.logistics.scm.auth.repository.UserRepository;
import com.logistics.scm.auth.repository.VerificationCodeRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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
                sendHtmlMail(code);
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

    private void sendHtmlMail(VerificationCode code) throws Exception {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        helper.setFrom(mailFrom);
        helper.setTo(code.getTargetValue());
        helper.setSubject(buildMailSubject(code.getPurpose()));
        helper.setText(buildMailHtml(code), true);
        javaMailSender.send(mimeMessage);
    }

    private String buildMailHtml(VerificationCode code) {
        String badge = switch (code.getPurpose()) {
            case SIGNUP -> "회원가입 인증";
            case FIND_ID -> "아이디 찾기";
            case RESET_PASSWORD -> "비밀번호 재설정";
        };
        String headline = switch (code.getPurpose()) {
            case SIGNUP -> "회원가입을 계속하려면 인증을 완료하세요";
            case FIND_ID -> "가입된 아이디 확인을 위해 인증이 필요합니다";
            case RESET_PASSWORD -> "비밀번호 재설정을 위해 인증이 필요합니다";
        };
        String description = switch (code.getPurpose()) {
            case SIGNUP -> "새로WMS 회원가입 요청이 접수되었습니다. 아래 인증번호를 입력하면 가입을 완료할 수 있습니다.";
            case FIND_ID -> "새로WMS 계정 확인 요청이 접수되었습니다. 아래 인증번호를 입력하면 가입된 아이디를 확인할 수 있습니다.";
            case RESET_PASSWORD -> "새로WMS 비밀번호 변경 요청이 접수되었습니다. 아래 인증번호를 입력하면 새 비밀번호를 설정할 수 있습니다.";
        };
        String actionLabel = switch (code.getPurpose()) {
            case SIGNUP -> "회원가입 인증번호";
            case FIND_ID -> "아이디 찾기 인증번호";
            case RESET_PASSWORD -> "비밀번호 재설정 인증번호";
        };

        return """
            <!doctype html>
            <html lang="ko">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>%s</title>
            </head>
            <body style="margin:0;padding:0;background:#f3f6fb;font-family:'Segoe UI',Arial,sans-serif;color:#0f172a;">
              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f3f6fb;padding:32px 16px;">
                <tr>
                  <td align="center">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:640px;background:#ffffff;border-radius:24px;overflow:hidden;box-shadow:0 18px 45px rgba(15,23,42,0.12);">
                      <tr>
                        <td style="background:linear-gradient(135deg,#0f172a 0%%,#1e3a8a 100%%);padding:28px 32px 32px 32px;">
                          <div style="display:inline-block;padding:7px 12px;border-radius:999px;background:rgba(255,255,255,0.14);color:#dbeafe;font-size:12px;font-weight:700;letter-spacing:0.02em;">%s</div>
                          <h1 style="margin:18px 0 8px 0;color:#ffffff;font-size:28px;line-height:1.3;font-weight:800;">새로WMS 인증 안내</h1>
                          <p style="margin:0;color:#cbd5e1;font-size:15px;line-height:1.7;">%s</p>
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:32px;">
                          <h2 style="margin:0 0 12px 0;color:#0f172a;font-size:22px;line-height:1.4;font-weight:800;">%s</h2>
                          <p style="margin:0 0 24px 0;color:#475569;font-size:15px;line-height:1.8;">%s</p>

                          <div style="border:1px solid #dbe7ff;border-radius:20px;background:linear-gradient(180deg,#eff6ff 0%%,#ffffff 100%%);padding:24px;text-align:center;">
                            <div style="margin-bottom:10px;color:#2563eb;font-size:13px;font-weight:700;letter-spacing:0.04em;">%s</div>
                            <div style="display:inline-block;padding:14px 22px;border-radius:16px;background:#0f172a;color:#ffffff;font-size:34px;font-weight:900;letter-spacing:0.24em;">%s</div>
                            <div style="margin-top:14px;color:#475569;font-size:14px;line-height:1.6;">인증번호는 <strong style="color:#0f172a;">%d분</strong> 동안 유효합니다.</div>
                          </div>

                          <div style="margin-top:24px;padding:18px 20px;border-radius:16px;background:#f8fafc;border:1px solid #e2e8f0;">
                            <div style="color:#0f172a;font-size:14px;font-weight:700;margin-bottom:8px;">확인사항</div>
                            <ul style="padding-left:18px;margin:0;color:#475569;font-size:14px;line-height:1.8;">
                              <li>인증번호는 요청하신 화면에서만 입력해주세요.</li>
                              <li>본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.</li>
                              <li>반복 요청 시 가장 최근에 발급된 인증번호만 유효합니다.</li>
                            </ul>
                          </div>
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:0 32px 32px 32px;">
                          <div style="border-top:1px solid #e2e8f0;padding-top:20px;color:#64748b;font-size:13px;line-height:1.8;">
                            본 메일은 새로WMS 시스템에서 자동 발송되었습니다.<br/>
                            문의가 필요한 경우 운영 담당자에게 연락해주세요.
                          </div>
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """.formatted(
            buildMailSubject(code.getPurpose()),
            badge,
            badge,
            headline,
            description,
            actionLabel,
            code.getVerificationCode(),
            expireMinutes
        );
    }
}
