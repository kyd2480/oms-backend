package com.logistics.scm.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationConfirmResponse {
    private boolean success;
    private String message;
    private String verificationToken;
    private String username;
    private String name;

    public static VerificationConfirmResponse success(String message, String verificationToken) {
        return VerificationConfirmResponse.builder()
            .success(true)
            .message(message)
            .verificationToken(verificationToken)
            .build();
    }

    public static VerificationConfirmResponse foundUser(String message, String username, String name) {
        return VerificationConfirmResponse.builder()
            .success(true)
            .message(message)
            .username(username)
            .name(name)
            .build();
    }

    public static VerificationConfirmResponse failure(String message) {
        return VerificationConfirmResponse.builder()
            .success(false)
            .message(message)
            .build();
    }
}
