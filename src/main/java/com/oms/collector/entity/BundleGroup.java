package com.oms.collector.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 묶음 그룹 Entity
 *
 * 같은 수취인+주소로 온 여러 주문을 합포장 그룹으로 관리
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "bundle_groups")
@EntityListeners(AuditingEntityListener.class)
public class BundleGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "bundle_id")
    private UUID bundleId;

    // 묶음 식별 키 (수취인명+연락처+주소 해시)
    @Column(name = "bundle_key", nullable = false, length = 200)
    private String bundleKey;

    // 대표 주문번호
    @Column(name = "representative_order_no", length = 100)
    private String representativeOrderNo;

    // 묶인 주문번호들 (comma-separated)
    @Column(name = "order_nos", nullable = false, columnDefinition = "TEXT")
    private String orderNos;

    // 수취인 정보 (조회용 캐싱)
    @Column(name = "recipient_name", length = 100)
    private String recipientName;

    @Column(name = "recipient_phone", length = 20)
    private String recipientPhone;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    // 묶음 상태
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private BundleStatus status = BundleStatus.BUNDLED;

    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    public enum BundleStatus {
        BUNDLED,    // 묶음 완료
        RELEASED,   // 묶음 해제
        SHIPPED     // 출고 완료
    }

    // 주문번호 목록 → 배열
    public String[] getOrderNoArray() {
        if (orderNos == null || orderNos.isBlank()) return new String[0];
        return orderNos.split(",");
    }

    public int getOrderCount() {
        return getOrderNoArray().length;
    }
}
