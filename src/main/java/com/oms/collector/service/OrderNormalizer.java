package com.oms.collector.service;

import com.oms.collector.dto.CollectedOrder;
import com.oms.collector.dto.CollectedOrderItem;
import com.oms.collector.entity.Order;
import com.oms.collector.entity.OrderItem;
import com.oms.collector.entity.RawOrder;
import com.oms.collector.entity.SalesChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/**
 * 주문 정규화 서비스
 * 
 * 수집된 주문(CollectedOrder) → OMS 표준 주문(Order)으로 변환
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderNormalizer {
    
    private final OrderSequenceService sequenceService;
    private final ProductMapper productMapper;
    
    /**
     * 수집된 주문을 OMS 표준 형식으로 정규화
     */
    public Order normalize(CollectedOrder collected, RawOrder rawOrder, SalesChannel channel) {
        log.debug("🔄 주문 정규화 시작: {}", collected.getChannelOrderNo());
        
        // OMS 주문번호 생성
        String orderNo = sequenceService.generateOrderNo();
        
        // Order Entity 생성
        Order order = Order.builder()
            .orderNo(orderNo)
            .rawOrder(rawOrder)
            .channel(channel)
            .channelOrderNo(collected.getChannelOrderNo())
            .build();
        
        // 고객 정보
        order.setCustomerName(collected.getCustomerName());
        order.setCustomerPhone(normalizePhone(collected.getCustomerPhone()));
        order.setCustomerEmail(collected.getCustomerEmail());
        
        // 배송 정보
        order.setRecipientName(collected.getRecipientName());
        order.setRecipientPhone(normalizePhone(collected.getRecipientPhone()));
        order.setPostalCode(collected.getPostalCode());
        order.setAddress(collected.getAddress());
        order.setAddressDetail(collected.getAddressDetail());
        order.setDeliveryMemo(collected.getDeliveryMemo());
        
        // 금액 정보
        order.setTotalAmount(collected.getTotalAmount());
        order.setPaymentAmount(collected.getPaymentAmount());
        order.setShippingFee(collected.getShippingFee());
        order.setDiscountAmount(collected.getDiscountAmount());
        
        // 상태 매핑
        order.setOrderStatus(mapOrderStatus(collected.getStatus()));
        order.setPaymentStatus(mapPaymentStatus(collected.getPaymentStatus()));
        
        // 날짜
        order.setOrderedAt(collected.getOrderedAt());
        order.setPaidAt(collected.getPaidAt());
        
        // 주문 상품 정규화
        collected.getItems().forEach(item -> {
            OrderItem orderItem = normalizeItem(item);
            order.addItem(orderItem);
        });
        
        log.info("✅ 주문 정규화 완료: {} → {}", collected.getChannelOrderNo(), orderNo);
        
        return order;
    }
    
    /**
     * 주문 상품 정규화
     */
    private OrderItem normalizeItem(CollectedOrderItem collected) {
        // 상품 코드 매핑
        String productCode = productMapper.mapToProductCode(collected.getChannelProductCode());
        
        OrderItem item = OrderItem.builder()
            .productCode(productCode)
            .channelProductCode(collected.getChannelProductCode())
            .productName(collected.getProductName())
            .optionName(collected.getOptionName())
            .quantity(collected.getQuantity())
            .unitPrice(collected.getUnitPrice())
            .totalPrice(collected.getTotalPrice())
            .build();
        
        // 총액 재계산 (검증)
        item.calculateTotalPrice();
        
        return item;
    }
    
    /**
     * 전화번호 정규화
     * 
     * 010-1234-5678 형식으로 통일
     */
    private String normalizePhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return null;
        }
        
        // 숫자만 추출
        String digits = phone.replaceAll("[^0-9]", "");
        
        // 010-XXXX-XXXX 형식
        if (digits.length() == 11 && digits.startsWith("010")) {
            return String.format("%s-%s-%s", 
                digits.substring(0, 3),
                digits.substring(3, 7),
                digits.substring(7, 11)
            );
        }
        
        // 02-XXX-XXXX 또는 02-XXXX-XXXX 형식 (서울)
        if (digits.length() >= 9 && digits.startsWith("02")) {
            if (digits.length() == 9) {
                return String.format("%s-%s-%s",
                    digits.substring(0, 2),
                    digits.substring(2, 5),
                    digits.substring(5, 9)
                );
            } else {
                return String.format("%s-%s-%s",
                    digits.substring(0, 2),
                    digits.substring(2, 6),
                    digits.substring(6, 10)
                );
            }
        }
        
        // 기타 지역번호 (XXX-XXX-XXXX 또는 XXX-XXXX-XXXX)
        if (digits.length() == 10) {
            return String.format("%s-%s-%s",
                digits.substring(0, 3),
                digits.substring(3, 6),
                digits.substring(6, 10)
            );
        }
        
        // 형식이 맞지 않으면 원본 반환
        log.warn("⚠️ 전화번호 형식 불일치: {}", phone);
        return phone;
    }
    
    /**
     * 주문 상태 매핑
     * 
     * 판매처별 상태 → OMS 표준 상태
     */
    private Order.OrderStatus mapOrderStatus(String channelStatus) {
        if (channelStatus == null) {
            return Order.OrderStatus.PENDING;
        }
        
        return switch (channelStatus.toUpperCase()) {
            case "PAYMENT_WAITING", "PENDING" -> Order.OrderStatus.PENDING;
            case "PRODUCT_PREPARE", "CONFIRMED" -> Order.OrderStatus.CONFIRMED;
            case "PAYED", "PAID" -> Order.OrderStatus.PENDING; // 결제완료 = 주문접수(PENDING), 재고할당은 별도
            case "DELIVERING", "SHIPPING" -> Order.OrderStatus.SHIPPED;
            case "DELIVERED", "COMPLETE" -> Order.OrderStatus.DELIVERED;
            case "CANCELED", "CANCELLED" -> Order.OrderStatus.CANCELLED;
            default -> {
                log.warn("⚠️ 알 수 없는 주문 상태: {} (PENDING으로 처리)", channelStatus);
                yield Order.OrderStatus.PENDING;
            }
        };
    }
    
    /**
     * 결제 상태 매핑
     */
    private Order.PaymentStatus mapPaymentStatus(String channelPaymentStatus) {
        if (channelPaymentStatus == null) {
            return Order.PaymentStatus.PENDING;
        }
        
        return switch (channelPaymentStatus.toUpperCase()) {
            case "PENDING", "WAITING" -> Order.PaymentStatus.PENDING;
            case "PAID", "PAYED", "COMPLETE" -> Order.PaymentStatus.PAID;
            case "CANCELED", "CANCELLED" -> Order.PaymentStatus.CANCELLED;
            case "REFUNDED", "REFUND" -> Order.PaymentStatus.REFUNDED;
            default -> {
                log.warn("⚠️ 알 수 없는 결제 상태: {} (PENDING으로 처리)", channelPaymentStatus);
                yield Order.PaymentStatus.PENDING;
            }
        };
    }
}
