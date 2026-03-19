package com.logistics.scm.auth.dto;

import com.logistics.scm.auth.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 사용자 정보 DTO
 * 
 * @author OMS Team
 * @since 2025-02-03
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {

    private UUID userId;
    private String username;
    private String name;
    private String email;
    private String role;
    private Boolean enabled;
    private LocalDateTime lastLoginAt;

    /**
     * Entity를 DTO로 변환
     */
    public static UserDTO from(User user) {
        return UserDTO.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .enabled(user.getEnabled())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }
}
