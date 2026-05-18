package com.logistics.scm.auth.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "verification_codes", indexes = {
    @Index(name = "idx_verification_lookup", columnList = "purpose,method,target_value,username,used,expires_at")
})
public class VerificationCode {

    public enum Purpose {
        SIGNUP,
        FIND_ID,
        RESET_PASSWORD
    }

    public enum Method {
        EMAIL,
        PHONE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "verification_id", columnDefinition = "uuid")
    private UUID verificationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private Purpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20)
    private Method method;

    @Column(name = "target_value", nullable = false, length = 120)
    private String targetValue;

    @Column(name = "username", length = 50)
    private String username;

    @Column(name = "verification_code", nullable = false, length = 10)
    private String verificationCode;

    @Column(name = "used", nullable = false)
    private boolean used = false;

    @Column(name = "verified", nullable = false)
    private boolean verified = false;

    @Column(name = "verification_token", length = 80)
    private String verificationToken;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    public static VerificationCode create(Purpose purpose, Method method, String targetValue, String username, String code, int expireMinutes) {
        VerificationCode item = new VerificationCode();
        item.purpose = purpose;
        item.method = method;
        item.targetValue = targetValue;
        item.username = username;
        item.verificationCode = code;
        item.createdAt = LocalDateTime.now();
        item.expiresAt = item.createdAt.plusMinutes(expireMinutes);
        item.used = false;
        item.verified = false;
        return item;
    }

    public boolean isExpired() {
        return expiresAt == null || expiresAt.isBefore(LocalDateTime.now());
    }

    public void markVerified(String token) {
        this.verified = true;
        this.verificationToken = token;
    }

    public void markUsed() {
        this.used = true;
        this.usedAt = LocalDateTime.now();
    }
}
