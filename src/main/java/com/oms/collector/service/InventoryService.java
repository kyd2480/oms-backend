package com.oms.collector.service;

import com.oms.collector.entity.InventoryTransaction;
import com.oms.collector.entity.Product;
import com.oms.collector.repository.InventoryTransactionRepository;
import com.oms.collector.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 재고 관리 Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {
    
    private final ProductRepository productRepository;
    private final InventoryTransactionRepository transactionRepository;
    
    /**
     * 입고 처리 (창고별)
     */
    @Transactional
    public Product processInboundWithWarehouse(UUID productId, int quantity, String warehouse, String location, String notes) {
        log.info("📦 입고 처리 (창고별): 상품 ID={}, 수량={}, 창고={}", productId, quantity, warehouse);
        
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        
        // 창고별 재고 증가
        switch (warehouse) {
            case "1.본사(안양)":
                product.setWarehouseStockAnyang(product.getWarehouseStockAnyang() + quantity);
                break;
            case "2.고백창고(이천)":
                product.setWarehouseStockIcheon(product.getWarehouseStockIcheon() + quantity);
                break;
            case "3.부천검수창고":
                product.setWarehouseStockBucheon(product.getWarehouseStockBucheon() + quantity);
                break;
        }
        
        // 총 재고 증가
        product.increaseStock(quantity);
        
        // 거래 내역 기록
        String detailedNotes = String.format("창고:%s | %s", warehouse, notes);
        InventoryTransaction transaction = InventoryTransaction.createInbound(
            product, quantity, location, detailedNotes
        );
        transactionRepository.save(transaction);
        
        Product saved = productRepository.save(product);
        
        log.info("✅ 입고 완료: {} - 창고:{}, 재고 {} → {}", 
            product.getProductName(), 
            warehouse,
            transaction.getBeforeStock(), 
            transaction.getAfterStock());
        
        return saved;
    }
    
    /**
     * 출고 처리 (창고별)
     */
    @Transactional
    public Product processOutboundWithWarehouse(UUID productId, int quantity, String warehouse, UUID orderId, String notes) {
        log.info("📤 출고 처리 (창고별): 상품 ID={}, 수량={}, 창고={}", productId, quantity, warehouse);
        
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        
        // 창고별 재고 확인 및 차감
        int warehouseStock = 0;
        switch (warehouse) {
            case "1.본사(안양)":
                warehouseStock = product.getWarehouseStockAnyang();
                if (warehouseStock < quantity) {
                    throw new IllegalStateException(warehouse + " 재고가 부족합니다. (현재: " + warehouseStock + "개)");
                }
                product.setWarehouseStockAnyang(warehouseStock - quantity);
                break;
            case "2.고백창고(이천)":
                warehouseStock = product.getWarehouseStockIcheon();
                if (warehouseStock < quantity) {
                    throw new IllegalStateException(warehouse + " 재고가 부족합니다. (현재: " + warehouseStock + "개)");
                }
                product.setWarehouseStockIcheon(warehouseStock - quantity);
                break;
            case "3.부천검수창고":
                warehouseStock = product.getWarehouseStockBucheon();
                if (warehouseStock < quantity) {
                    throw new IllegalStateException(warehouse + " 재고가 부족합니다. (현재: " + warehouseStock + "개)");
                }
                product.setWarehouseStockBucheon(warehouseStock - quantity);
                break;
        }
        
        // 총 재고 차감
        product.decreaseStock(quantity);
        
        // 거래 내역 기록
        String detailedNotes = String.format("창고:%s | %s", warehouse, notes);
        InventoryTransaction transaction = InventoryTransaction.createOutbound(
            product, quantity, orderId, detailedNotes
        );
        transactionRepository.save(transaction);
        
        Product saved = productRepository.save(product);
        
        log.info("✅ 출고 완료: {} - 창고:{}, 재고 {} → {}", 
            product.getProductName(), 
            warehouse,
            transaction.getBeforeStock(), 
            transaction.getAfterStock());
        
        return saved;
    }
    
    /**
     * 입고 처리
     */
    @Transactional
    public Product processInbound(UUID productId, int quantity, String location, String notes) {
        log.info("📦 입고 처리: 상품 ID={}, 수량={}", productId, quantity);
        
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        
        // 재고 증가
        product.increaseStock(quantity);
        
        // 거래 내역 기록
        InventoryTransaction transaction = InventoryTransaction.createInbound(
            product, quantity, location, notes
        );
        transactionRepository.save(transaction);
        
        Product saved = productRepository.save(product);
        
        log.info("✅ 입고 완료: {} - 재고 {} → {}", 
            product.getProductName(), 
            transaction.getBeforeStock(), 
            transaction.getAfterStock());
        
        return saved;
    }
    
    /**
     * 출고 처리
     */
    @Transactional
    public Product processOutbound(UUID productId, int quantity, UUID orderId, String notes) {
        log.info("📤 출고 처리: 상품 ID={}, 수량={}", productId, quantity);
        
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        
        // 재고 차감
        product.decreaseStock(quantity);
        
        // 거래 내역 기록
        InventoryTransaction transaction = InventoryTransaction.createOutbound(
            product, quantity, orderId, notes
        );
        transactionRepository.save(transaction);
        
        Product saved = productRepository.save(product);
        
        log.info("✅ 출고 완료: {} - 재고 {} → {}", 
            product.getProductName(), 
            transaction.getBeforeStock(), 
            transaction.getAfterStock());
        
        // 안전 재고 경고
        if (saved.isBelowSafetyStock()) {
            log.warn("⚠️ 안전 재고 미달: {} (현재: {}, 안전: {})", 
                saved.getProductName(), 
                saved.getAvailableStock(), 
                saved.getSafetyStock());
        }
        
        return saved;
    }
    
    /**
     * 재고 조정
     */
    @Transactional
    public Product adjustInventory(UUID productId, int quantity, String reason) {
        log.info("🔧 재고 조정: 상품 ID={}, 수량={}", productId, quantity);
        
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        
        // 재고 조정
        product.increaseStock(quantity);  // 음수 가능
        
        // 거래 내역 기록
        InventoryTransaction transaction = InventoryTransaction.createAdjustment(
            product, quantity, reason
        );
        transactionRepository.save(transaction);
        
        Product saved = productRepository.save(product);
        
        log.info("✅ 재고 조정 완료: {} - 재고 {} → {}", 
            product.getProductName(), 
            transaction.getBeforeStock(), 
            transaction.getAfterStock());
        
        return saved;
    }
    
    /**
     * 재고 예약 (주문 시)
     */
    @Transactional
    public void reserveStock(UUID productId, int quantity) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        
        product.reserveStock(quantity);
        productRepository.save(product);
        
        log.info("🔒 재고 예약: {} - {}개", product.getProductName(), quantity);
    }
    
    /**
     * 재고 예약 취소
     */
    @Transactional
    public void releaseReservedStock(UUID productId, int quantity) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        
        product.releaseReservedStock(quantity);
        productRepository.save(product);
        
        log.info("🔓 재고 예약 취소: {} - {}개", product.getProductName(), quantity);
    }
    
    /**
     * 안전 재고 미달 상품 조회
     */
    @Transactional(readOnly = true)
    public List<Product> getLowStockProducts() {
        return productRepository.findLowStockProducts();
    }
    
    /**
     * 재고 없는 상품 조회
     */
    @Transactional(readOnly = true)
    public List<Product> getOutOfStockProducts() {
        return productRepository.findOutOfStockProducts();
    }
    
    /**
     * 재고 거래 내역 조회
     */
    @Transactional(readOnly = true)
    public List<InventoryTransaction> getTransactionHistory(
            UUID productId, LocalDateTime startDate, LocalDateTime endDate) {
        
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        
        return transactionRepository.findByProductAndCreatedAtBetweenOrderByCreatedAtDesc(
            product, startDate, endDate
        );
    }
}
