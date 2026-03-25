package com.oms.collector.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 반품 엔티티
 *
 * 상태 흐름: REQUESTED → INSPECTING → COMPLETED / CANCELLED
 * 검수 결과: NORMAL(정상) → 원래 창고 입고
 *           DEFECTIVE(불량) → ANYANG(국내온라인 반품) 이동
 */
@Entity
@Table(name = "product_returns")  // 'returns'는 PostgreSQL 예약어 → product_returns 사용
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "return_id")
    private UUID returnId;

    /* ── 원본 주문 정보 ─────────────────────────────── */
    @Column(name = "order_no", nullable = false, length = 100)
    private String orderNo;

    @Column(name = "channel_name", length = 50)
    private String channelName;        // 쿠팡, 11번가, 스마트스토어 등

    @Column(name = "recipient_name", length = 100)
    private String recipientName;

    @Column(name = "recipient_phone", length = 20)
    private String recipientPhone;

    @Column(name = "product_name", columnDefinition = "TEXT")
    private String productName;

    @Column(name = "quantity")
    private Integer quantity;

    /* ── 반품 정보 ─────────────────────────────────── */
    @Enumerated(EnumType.STRING)
    @Column(name = "return_type", nullable = false, length = 20)
    private ReturnType returnType;     // CANCEL, REFUND, EXCHANGE

    @Column(name = "return_reason", columnDefinition = "TEXT")
    private String returnReason;       // 반품 사유

    @Column(name = "return_tracking_no", length = 100)
    private String returnTrackingNo;   // 반품 운송장번호

    @Column(name = "carrier_name", length = 50)
    private String carrierName;        // 택배사

    /* ── 상태 ──────────────────────────────────────── */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ReturnStatus status = ReturnStatus.REQUESTED;

    /* ── 검수 결과 ─────────────────────────────────── */
    @Enumerated(EnumType.STRING)
    @Column(name = "inspect_result", length = 20)
    private InspectResult inspectResult;  // NORMAL, DEFECTIVE

    @Column(name = "warehouse_code", length = 20)
    private String warehouseCode;         // 입고 창고 코드

    @Column(name = "inspect_memo", columnDefinition = "TEXT")
    private String inspectMemo;           // 검수 메모

    /* ── 환불/교환 ─────────────────────────────────── */
    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_type", length = 20)
    private ResolutionType resolutionType; // REFUND, EXCHANGE, NONE

    @Column(name = "refund_amount")
    private Integer refundAmount;          // 환불 금액

    @Column(name = "exchange_order_no", length = 100)
    private String exchangeOrderNo;        // 교환 주문번호

    @Column(name = "resolution_memo", columnDefinition = "TEXT")
    private String resolutionMemo;         // 처리 메모

    /* ── 메타 ──────────────────────────────────────── */
    @Column(name = "source", length = 20)
    @Builder.Default
    private String source = "MANUAL";      // MANUAL, COUPANG, STREET11

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /* ── Enum ──────────────────────────────────────── */
    public enum ReturnType {
        CANCEL,    // 취소
        REFUND,    // 환불
        EXCHANGE   // 교환
    }

    public enum ReturnStatus {
        REQUESTED,   // 접수
        INSPECTING,  // 검수중
        COMPLETED,   // 완료
        CANCELLED    // 취소됨
    }

    public enum InspectResult {
        NORMAL,    // 정상 → 원래 창고 입고
        DEFECTIVE  // 불량 → 안양 이동
    }

    public enum ResolutionType {
        REFUND,    // 환불
        EXCHANGE,  // 교환
        NONE       // 해당없음
    }
}
