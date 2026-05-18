package com.logistics.scm.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationSendResponse {
    private boolean success;
    private String message;
    private String deliveryMode;
    private String debugCode;

    public static VerificationSendResponse success(String message, String deliveryMode, String debugCode) {
        return VerificationSendResponse.builder()
            .success(true)
            .message(message)
            .deliveryMode(deliveryMode)
            .debugCode(debugCode)
            .build();
    }

    public static VerificationSendResponse failure(String message) {
        return VerificationSendResponse.builder()
            .success(false)
            .message(message)
            .build();
    }
}
