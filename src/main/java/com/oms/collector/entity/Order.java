package com.oms.collector.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 정규화된 주문 Entity
 * 
 * 판매처별 원본 데이터를 OMS 표준 형식으로 변환한 주문 정보
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "orders")
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"rawOrder", "channel", "hibernateLazyInitializer", "handler"})
public class Order {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "order_id")
    private UUID orderId;
    
    @Column(name = "order_no", unique = true, nullable = false, length = 100)
    private String orderNo;  // OMS-20240204-0001
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raw_order_id")
    private RawOrder rawOrder;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id")
    private SalesChannel channel;
    
    @Column(name = "channel_order_no", length = 100)
    private String channelOrderNo;
    
    // 고객 정보
    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;
    
    @Column(name = "customer_phone", length = 20)
    private String customerPhone;
    
    @Column(name = "customer_email", length = 100)
    private String customerEmail;
    
    // 배송 정보
    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName;
    
    @Column(name = "recipient_phone", nullable = false, length = 20)
    private String recipientPhone;
    
    @Column(name = "postal_code", length = 10)
    private String postalCode;
    
    @Column(name = "address", nullable = false)
    private String address;
    
    @Column(name = "address_detail")
    private String addressDetail;
    
    @Column(name = "delivery_memo", columnDefinition = "TEXT")
    private String deliveryMemo;
    
    // 금액 정보
    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;
    
    @Column(name = "payment_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal paymentAmount;
    
    @Column(name = "shipping_fee", precision = 10, scale = 2)
    private BigDecimal shippingFee = BigDecimal.ZERO;
    
    @Column(name = "discount_amount", precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;
    
    // 상태
    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", length = 20)
    private OrderStatus orderStatus = OrderStatus.PENDING;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;
    
    // 날짜
    @Column(name = "ordered_at", nullable = false)
    private LocalDateTime orderedAt;
    
    @Column(name = "paid_at")
    private LocalDateTime paidAt;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // 주문 상품 (One-to-Many)
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();
    
    // Enum: 주문 상태
    public enum OrderStatus {
        PENDING,      // 대기
        CONFIRMED,    // 확인
        SHIPPED,      // 배송중
        DELIVERED,    // 배송완료
        CANCELLED     // 취소
    }
    
    // Enum: 결제 상태
    public enum PaymentStatus {
        PENDING,      // 대기
        PAID,         // 결제완료
        CANCELLED,    // 취소
        REFUNDED      // 환불
    }
    
    // Business Methods
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }
    
    public void removeItem(OrderItem item) {
        items.remove(item);
        item.setOrder(null);
    }
    
    public void updateStatus(OrderStatus newStatus) {
        this.orderStatus = newStatus;
    }
    
    public void confirmPayment() {
        this.paymentStatus = PaymentStatus.PAID;
        this.paidAt = LocalDateTime.now();
    }
    
    public boolean isPaid() {
        return this.paymentStatus == PaymentStatus.PAID;
    }
    
    public boolean isCancelled() {
        return this.orderStatus == OrderStatus.CANCELLED;
    }
}
