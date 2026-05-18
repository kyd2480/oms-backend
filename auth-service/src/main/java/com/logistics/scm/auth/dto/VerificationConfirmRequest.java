package com.logistics.scm.auth.dto;

import lombok.Data;

@Data
public class VerificationConfirmRequest {
    private String method;
    private String email;
    private String phone;
    private String username;
    private String code;
}
