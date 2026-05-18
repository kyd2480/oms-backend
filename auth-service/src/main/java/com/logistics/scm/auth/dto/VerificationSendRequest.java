package com.logistics.scm.auth.dto;

import lombok.Data;

@Data
public class VerificationSendRequest {
    private String method;
    private String email;
    private String phone;
    private String username;
}
