package com.logistics.scm.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 로그인 응답 DTO
 * 
 * @author OMS Team
 * @since 2025-02-03
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private boolean success;
    private String token;
    private UserDTO user;
    private String message;

    public static LoginResponse success(String token, UserDTO user) {
        return LoginResponse.builder()
                .success(true)
                .token(token)
                .user(user)
                .message("로그인 성공")
                .build();
    }

    public static LoginResponse failure(String message) {
        return LoginResponse.builder()
                .success(false)
                .message(message)
                .build();
    }
}
