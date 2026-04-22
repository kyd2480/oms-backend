package com.oms.collector.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarrierContractDto {
    private UUID contractId;
    private String companyCode;
    private String carrierCode;
    private String carrierName;
    private String contractName;
    private Boolean isDefault;
    private Boolean enabled;
    private String apiBaseUrl;
    private String authKey;
    private String maskedAuthKey;
    private String seedKey;
    private String maskedSeedKey;
    private String customerNo;
    private String contractApprovalNo;
    private String officeSer;
    private String contentCode;
    private String senderCompanyName;
    private String senderTel;
    private String senderZip;
    private String senderAddress;
    private String senderAddressDetail;
    private String testYn;
    private String printYn;
    private String memo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
