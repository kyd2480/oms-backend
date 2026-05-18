package com.logistics.scm.auth.dto;

import lombok.Data;

@Data
public class ResetPasswordRequest {
    private String method;
    private String email;
    private String phone;
    private String username;
    private String code;
    private String newPassword;
}
