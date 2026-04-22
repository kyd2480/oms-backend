package com.logistics.scm.auth.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User Entity
 * 
 * 사용자 인증 및 권한 관리를 위한 엔티티
 * 
 * @author OMS Team
 * @since 2025-02-03
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "USERS", indexes = {
    @Index(name = "idx_username", columnList = "username"),
    @Index(name = "idx_email", columnList = "email")
})
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password", nullable = false, length = 100)
    private String password;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role = UserRole.USER;

    @Column(name = "company_code", length = 20)
    private String companyCode;

    /** 허용된 페이지 키 목록 (쉼표 구분). null = 전체 허용 */
    @Column(name = "page_permissions", columnDefinition = "TEXT")
    private String pagePermissions;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Enum for User Role
    public enum UserRole {
        ADMIN,      // 관리자
        MANAGER,    // 매니저
        OPERATOR,   // 운영자
        USER        // 일반 사용자
    }

    // Business Methods
    public void updateLastLoginTime() {
        this.lastLoginAt = LocalDateTime.now();
    }

    public boolean isAdmin() {
        return this.role == UserRole.ADMIN;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    // Builder Pattern
    public static User create(String username, String password, String name, String email, UserRole role, String companyCode) {
        User user = new User();
        user.username = username;
        user.password = password;
        user.name = name;
        user.email = email;
        user.role = role;
        user.companyCode = companyCode != null ? companyCode.toUpperCase() : "C00";
        user.enabled = true;
        return user;
    }
}
