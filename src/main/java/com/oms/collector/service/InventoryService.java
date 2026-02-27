package com.oms.collector.service;

import com.oms.collector.entity.InventoryTransaction;
import com.oms.collector.entity.Product;
import com.oms.collector.entity.Warehouse;
import com.oms.collector.repository.InventoryTransactionRepository;
import com.oms.collector.repository.ProductRepository;
import com.oms.collector.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 재고 관리 Service
 *
 * 변경사항 (기존 코드 대비):
 *   1. WarehouseRepository 주입 추가
 *   2. processInboundWithWarehouse  - switch 하드코딩 → DB 창고 조회로 교체
 *   3. processOutboundWithWarehouse - switch 하드코딩 → DB 창고 조회로 교체
 *   4. 나머지 메서드 전부 동일
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final ProductRepository productRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final WarehouseRepository warehouseRepository;  // ✅ 추가

    // 레거시 Product 컬럼과 매핑되는 창고 코드
    private static final String ANYANG  = "ANYANG";
    private static final String ICHEON  = "ICHEON_BOX";
    private static final String ICHEON2 = "ICHEON_PCS";
    private static final String BUCHEON = "BUCHEON";

    /**
     * 입고 처리 (창고별)
     *
     * warehouse 파라미터: 기존 "1.본사(안양)" 같은 문자열 대신
     *                     창고 코드 "ANYANG" 방식으로 변경
     *                     (프론트에서 warehouse.code 전송)
     */
    @Transactional
    public Product processInboundWithWarehouse(UUID productId, int quantity,
                                               String warehouseCode, String location, String notes) {
        log.info("📦 입고 처리 (창고별): 상품 ID={}, 수량={}, 창고={}", productId, quantity, warehouseCode);

        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));

        // ✅ DB에서 창고 조회 (하드코딩 switch 완전 대체)
        Warehouse warehouse = warehouseRepository.findByCode(warehouseCode)
            .filter(w -> Boolean.TRUE.equals(w.getIsActive()))
            .orElseThrow(() -> new IllegalArgumentException(
                "존재하지 않거나 비활성화된 창고입니다: " + warehouseCode));

        // 레거시 3개 컬럼 창고는 개별 컬럼 업데이트, 그 외는 총재고만 반영
        switch (warehouse.getCode()) {
            case ANYANG:
                product.setWarehouseStockAnyang(product.getWarehouseStockAnyang() + quantity);
                break;
            case ICHEON:
            case ICHEON2:
                product.setWarehouseStockIcheon(product.getWarehouseStockIcheon() + quantity);
                break;
            case BUCHEON:
                product.setWarehouseStockBucheon(product.getWarehouseStockBucheon() + quantity);
                break;
            default:
                // 신규 창고: 총 재고에만 반영 (아래 increaseStock에서 처리)
                log.debug("신규 창고 입고 (총재고만 반영): {}", warehouse.getCode());
        }

        // 총 재고 증가
        product.increaseStock(quantity);

        // 거래 내역 기록
        String detailedNotes = String.format("창고:%s(%s) | %s",
            warehouse.getName(), warehouse.getCode(), notes != null ? notes : "");
        InventoryTransaction transaction = InventoryTransaction.createInbound(
            product, quantity, location != null ? location : warehouse.getName(), detailedNotes);
        transactionRepository.save(transaction);

        Product saved = productRepository.save(product);

        log.info("✅ 입고 완료: {} - 창고:{}, 재고 {} → {}",
            product.getProductName(), warehouse.getName(),
            transaction.getBeforeStock(), transaction.getAfterStock());

        return saved;
    }

    /**
     * 출고 처리 (창고별)
     */
    @Transactional
    public Product processOutboundWithWarehouse(UUID productId, int quantity,
                                                String warehouseCode, UUID orderId, String notes) {
        log.info("📤 출고 처리 (창고별): 상품 ID={}, 수량={}, 창고={}", productId, quantity, warehouseCode);

        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));

        // ✅ DB에서 창고 조회 (하드코딩 switch 완전 대체)
        Warehouse warehouse = warehouseRepository.findByCode(warehouseCode)
            .filter(w -> Boolean.TRUE.equals(w.getIsActive()))
            .orElseThrow(() -> new IllegalArgumentException(
                "존재하지 않거나 비활성화된 창고입니다: " + warehouseCode));

        // 레거시 3개 컬럼 창고는 개별 컬럼 차감 + 재고 부족 검증
        int warehouseStock;
        switch (warehouse.getCode()) {
            case ANYANG:
                warehouseStock = product.getWarehouseStockAnyang();
                if (warehouseStock < quantity) {
                    throw new IllegalStateException(
                        warehouse.getName() + " 재고가 부족합니다. (현재: " + warehouseStock + "개)");
                }
                product.setWarehouseStockAnyang(warehouseStock - quantity);
                break;
            case ICHEON:
            case ICHEON2:
                warehouseStock = product.getWarehouseStockIcheon();
                if (warehouseStock < quantity) {
                    throw new IllegalStateException(
                        warehouse.getName() + " 재고가 부족합니다. (현재: " + warehouseStock + "개)");
                }
                product.setWarehouseStockIcheon(warehouseStock - quantity);
                break;
            case BUCHEON:
                warehouseStock = product.getWarehouseStockBucheon();
                if (warehouseStock < quantity) {
                    throw new IllegalStateException(
                        warehouse.getName() + " 재고가 부족합니다. (현재: " + warehouseStock + "개)");
                }
                product.setWarehouseStockBucheon(warehouseStock - quantity);
                break;
            default:
                // 신규 창고: 총 재고에서 차감 (아래 decreaseStock에서 처리)
                if (product.getTotalStock() < quantity) {
                    throw new IllegalStateException(
                        warehouse.getName() + " 재고가 부족합니다. (현재: " + product.getTotalStock() + "개)");
                }
                log.debug("신규 창고 출고 (총재고만 차감): {}", warehouse.getCode());
        }

        // 총 재고 차감
        product.decreaseStock(quantity);

        // 거래 내역 기록
        String detailedNotes = String.format("창고:%s(%s) | %s",
            warehouse.getName(), warehouse.getCode(), notes != null ? notes : "");
        InventoryTransaction transaction = InventoryTransaction.createOutbound(
            product, quantity, orderId, detailedNotes);
        transactionRepository.save(transaction);

        Product saved = productRepository.save(product);

        log.info("✅ 출고 완료: {} - 창고:{}, 재고 {} → {}",
            product.getProductName(), warehouse.getName(),
            transaction.getBeforeStock(), transaction.getAfterStock());

        if (saved.isBelowSafetyStock()) {
            log.warn("⚠️ 안전 재고 미달: {} (현재: {}, 안전: {})",
                saved.getProductName(), saved.getAvailableStock(), saved.getSafetyStock());
        }

        return saved;
    }

    // ── 아래는 기존 코드와 100% 동일 ─────────────────────────────────────────

    /**
     * 입고 처리
     */
    @Transactional
    public Product processInbound(UUID productId, int quantity, String location, String notes) {
        log.info("📦 입고 처리: 상품 ID={}, 수량={}", productId, quantity);

        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));

        product.increaseStock(quantity);

        InventoryTransaction transaction = InventoryTransaction.createInbound(
            product, quantity, location, notes);
        transactionRepository.save(transaction);

        Product saved = productRepository.save(product);

        log.info("✅ 입고 완료: {} - 재고 {} → {}",
            product.getProductName(), transaction.getBeforeStock(), transaction.getAfterStock());

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

        product.decreaseStock(quantity);

        InventoryTransaction transaction = InventoryTransaction.createOutbound(
            product, quantity, orderId, notes);
        transactionRepository.save(transaction);

        Product saved = productRepository.save(product);

        log.info("✅ 출고 완료: {} - 재고 {} → {}",
            product.getProductName(), transaction.getBeforeStock(), transaction.getAfterStock());

        if (saved.isBelowSafetyStock()) {
            log.warn("⚠️ 안전 재고 미달: {} (현재: {}, 안전: {})",
                saved.getProductName(), saved.getAvailableStock(), saved.getSafetyStock());
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

        product.increaseStock(quantity);

        InventoryTransaction transaction = InventoryTransaction.createAdjustment(
            product, quantity, reason);
        transactionRepository.save(transaction);

        Product saved = productRepository.save(product);

        log.info("✅ 재고 조정 완료: {} - 재고 {} → {}",
            product.getProductName(), transaction.getBeforeStock(), transaction.getAfterStock());

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
            product, startDate, endDate);
    }

    /**
     * 최근 거래 내역 조회 (전체)
     */
    @Transactional(readOnly = true)
    public List<InventoryTransaction> getRecentTransactions(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return transactionRepository.findRecentTransactionsWithProduct(pageable);
    }

    /**
     * 거래 내역 검색 (상품명, SKU, 바코드)
     */
    @Transactional(readOnly = true)
    public List<InventoryTransaction> searchTransactions(String keyword, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return transactionRepository.searchTransactionsWithProduct(keyword, pageable);
    }
}
