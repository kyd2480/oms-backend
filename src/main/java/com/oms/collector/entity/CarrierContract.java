package com.oms.collector.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "carrier_contracts")
@EntityListeners(AuditingEntityListener.class)
public class CarrierContract {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "contract_id")
    private UUID contractId;

    @Column(name = "company_code", nullable = false, length = 20)
    private String companyCode;

    @Column(name = "carrier_code", nullable = false, length = 50)
    private String carrierCode;

    @Column(name = "carrier_name", nullable = false, length = 100)
    private String carrierName;

    @Column(name = "contract_name", nullable = false, length = 100)
    private String contractName;

    @Default
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    @Default
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "api_base_url", length = 300)
    private String apiBaseUrl;

    @Column(name = "auth_key", length = 500)
    private String authKey;

    @Column(name = "seed_key", length = 500)
    private String seedKey;

    @Column(name = "customer_no", length = 100)
    private String customerNo;

    @Column(name = "contract_approval_no", length = 100)
    private String contractApprovalNo;

    @Column(name = "office_ser", length = 50)
    private String officeSer;

    @Column(name = "content_code", length = 50)
    private String contentCode;

    @Column(name = "sender_company_name", length = 100)
    private String senderCompanyName;

    @Column(name = "sender_tel", length = 50)
    private String senderTel;

    @Column(name = "sender_zip", length = 20)
    private String senderZip;

    @Column(name = "sender_address", length = 300)
    private String senderAddress;

    @Column(name = "sender_address_detail", length = 300)
    private String senderAddressDetail;

    @Column(name = "test_yn", length = 1)
    private String testYn;

    @Column(name = "print_yn", length = 1)
    private String printYn;

    @Column(name = "memo", length = 500)
    private String memo;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
