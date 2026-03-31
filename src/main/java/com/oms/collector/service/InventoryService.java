package com.oms.collector.service;

import com.oms.collector.entity.InventoryTransaction;
import com.oms.collector.entity.Product;
import com.oms.collector.entity.ProductWarehouseStock;
import com.oms.collector.entity.Warehouse;
import com.oms.collector.repository.InventoryTransactionRepository;
import com.oms.collector.repository.ProductRepository;
import com.oms.collector.repository.ProductWarehouseStockRepository;
import com.oms.collector.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
    private final WarehouseRepository warehouseRepository;
    private final ProductWarehouseStockRepository warehouseStockRepository; // ✅ 창고별 재고

    // 레거시 Product 컬럼과 매핑되는 창고 코드 (기존 3개만 유지)
    private static final String ANYANG  = "ANYANG";
    private static final String ICHEON  = "ICHEON_BOX";
    private static final String ICHEON2 = "ICHEON_PCS";   // 별칭 혹은 레거시
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

        // ANYANG / ICHEON_BOX / ICHEON_PCS / BUCHEON 은 Product 레거시 컬럼
        // 그 외 모든 창고는 product_warehouse_stock 테이블
        switch (warehouse.getCode()) {
            case "ANYANG":
                product.setWarehouseStockAnyang(product.getWarehouseStockAnyang() + quantity);
                break;
            case "ICHEON_BOX":
            case "ICHEON_PCS":
                product.setWarehouseStockIcheon(product.getWarehouseStockIcheon() + quantity);
                break;
            case "BUCHEON":
                product.setWarehouseStockBucheon(product.getWarehouseStockBucheon() + quantity);
                break;
            default:
                // 신규 창고 전부: product_warehouse_stock 테이블에 기록
                updateWarehouseStock(product.getProductId(), warehouse.getCode(),
                    warehouse.getName(), quantity);
                log.debug("신규 창고 입고 (warehouse_stock 테이블): {} {}", warehouse.getCode(), quantity);
        }

        // 거래 내역 기록 (총재고 변경 전에 생성해야 beforeStock이 정확함)
        String detailedNotes = String.format("창고:%s(%s) | %s",
            warehouse.getName(), warehouse.getCode(), notes != null ? notes : "");
        InventoryTransaction transaction = InventoryTransaction.createInbound(
            product, quantity, location != null ? location : warehouse.getName(), detailedNotes);
        transactionRepository.save(transaction);

        // 총 재고 증가
        product.increaseStock(quantity);

        Product saved = productRepository.save(product);

        log.info("✅ 입고 완료: {} - 창고:{}, 재고 {} → {}",
            product.getProductName(), warehouse.getName(),
            transaction.getBeforeStock(), transaction.getAfterStock());

        return saved;
    }

    /**
     * 접수 시 창고 입고 (REQUIRES_NEW) — 독립 트랜잭션으로 실행
     * 실패 시 예외를 던져 호출자가 접수 전체를 중단할 수 있도록 함
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void tryAcceptanceInbound(UUID productId, int quantity,
                                     String warehouseCode, String notes) {
        Warehouse warehouse = warehouseRepository.findByCode(warehouseCode)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 창고: " + warehouseCode));
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + productId));
        updateWarehouseStock(product.getProductId(), warehouse.getCode(),
            warehouse.getName(), quantity);
        String detailedNotes = String.format("창고:%s(%s) | %s",
            warehouse.getName(), warehouse.getCode(), notes != null ? notes : "");
        // 접수 입고는 총재고 변경 없음 → beforeStock = afterStock = totalStock
        InventoryTransaction tx = InventoryTransaction.builder()
            .product(product)
            .transactionType("IN")
            .quantity(quantity)
            .beforeStock(product.getTotalStock())
            .afterStock(product.getTotalStock())
            .toLocation(warehouse.getName())
            .notes(detailedNotes)
            .build();
        transactionRepository.save(tx);
        productRepository.save(product);
        log.info("✅ 접수 입고: {} {}개 → {}", product.getProductName(), quantity, warehouse.getName());
    }

    /**
     * 창고 간 이동 — fromWarehouse 재고 차감, toWarehouse 재고 증가
     * normalTransfer=true  → toWarehouse 입고 시 총재고/가용재고도 증가 (정상 검수)
     * normalTransfer=false → 창고별 재고만 이동, 총재고 불변 (불량 검수)
     */
    @Transactional
    public void warehouseTransfer(UUID productId, int quantity,
                                  String fromWarehouseCode, String toWarehouseCode,
                                  boolean normalTransfer, String notes) {
        log.info("🔄 창고 이동: {} {}개 {} → {}", productId, quantity, fromWarehouseCode, toWarehouseCode);

        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + productId));

        Warehouse fromWh = warehouseRepository.findByCode(fromWarehouseCode)
            .orElseThrow(() -> new IllegalArgumentException("출발 창고 없음: " + fromWarehouseCode));
        Warehouse toWh = warehouseRepository.findByCode(toWarehouseCode)
            .orElseThrow(() -> new IllegalArgumentException("도착 창고 없음: " + toWarehouseCode));

        // 출발 창고 재고 차감 (레거시 컬럼 vs warehouse_stock 테이블 구분)
        applyWarehouseStockDelta(product, fromWh.getCode(), fromWh.getName(), -quantity);

        // 도착 창고 재고 증가 (레거시 컬럼 vs warehouse_stock 테이블 구분)
        applyWarehouseStockDelta(product, toWh.getCode(), toWh.getName(), quantity);

        // 거래 내역 기록 (총재고 변경 전에 생성해야 beforeStock이 정확함)
        String detailedNotes = String.format("창고이동:%s→%s | %s",
            fromWh.getName(), toWh.getName(), notes != null ? notes : "");
        int beforeTotal = product.getTotalStock();
        InventoryTransaction tx = InventoryTransaction.builder()
            .product(product)
            .transactionType(normalTransfer ? "IN" : "MOVE")
            .quantity(quantity)
            .beforeStock(beforeTotal)
            .afterStock(normalTransfer ? beforeTotal + quantity : beforeTotal)
            .fromLocation(fromWh.getName())
            .toLocation(toWh.getName())
            .notes(detailedNotes)
            .build();
        transactionRepository.save(tx);

        // 정상 검수 이동: 총재고/가용재고 증가 (ANYANG_KO_RETURN은 총재고 미반영이었으므로 여기서 반영)
        if (normalTransfer) {
            product.increaseStock(quantity);
        }

        productRepository.save(product);

        log.info("✅ 창고 이동 완료: {} {}개 {} → {}", product.getProductName(), quantity,
            fromWh.getName(), toWh.getName());
    }

    /**
     * 레거시 창고(ANYANG/ICHEON_BOX/ICHEON_PCS/BUCHEON)는 Product 컬럼 직접 수정,
     * 그 외 신규 창고는 product_warehouse_stock 테이블 사용
     */
    private void applyWarehouseStockDelta(Product product, String warehouseCode,
                                          String warehouseName, int delta) {
        switch (warehouseCode) {
            case "ANYANG":
                product.setWarehouseStockAnyang(product.getWarehouseStockAnyang() + delta);
                break;
            case "ICHEON_BOX":
            case "ICHEON_PCS":
                product.setWarehouseStockIcheon(product.getWarehouseStockIcheon() + delta);
                break;
            case "BUCHEON":
                product.setWarehouseStockBucheon(product.getWarehouseStockBucheon() + delta);
                break;
            default:
                updateWarehouseStock(product.getProductId(), warehouseCode, warehouseName, delta);
        }
    }

    /**
     * 불량 반품 입고 — 창고별 재고만 기록, 총재고/가용재고 변경 없음
     */
    @Transactional
    public void processDefectiveInbound(UUID productId, int quantity,
                                        String warehouseCode, String notes) {
        log.info("📦 불량 입고 (창고별 재고만): 상품 ID={}, 수량={}, 창고={}", productId, quantity, warehouseCode);

        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));

        Warehouse warehouse = warehouseRepository.findByCode(warehouseCode)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 창고: " + warehouseCode));

        // 창고별 재고만 업데이트 (총재고/가용재고 변경 없음)
        updateWarehouseStock(product.getProductId(), warehouse.getCode(),
            warehouse.getName(), quantity);

        // 거래 내역 기록
        String detailedNotes = String.format("창고:%s(%s) | %s",
            warehouse.getName(), warehouse.getCode(), notes != null ? notes : "");
        InventoryTransaction transaction = InventoryTransaction.createInbound(
            product, quantity, warehouse.getName(), detailedNotes);
        transactionRepository.save(transaction);
        productRepository.save(product);

        log.info("✅ 불량 입고 완료: {} - 창고:{}", product.getProductName(), warehouse.getName());
    }
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

        // 레거시 컬럼 창고는 개별 컬럼 차감, 신규 창고는 product_warehouse_stock 테이블
        int warehouseStock;
        switch (warehouse.getCode()) {
            case "ANYANG":
                warehouseStock = product.getWarehouseStockAnyang();
                if (warehouseStock < quantity)
                    throw new IllegalStateException(warehouse.getName() + " 재고 부족 (현재: " + warehouseStock + "개)");
                product.setWarehouseStockAnyang(warehouseStock - quantity);
                break;
            case "ICHEON_BOX":
            case "ICHEON_PCS":
                warehouseStock = product.getWarehouseStockIcheon();
                if (warehouseStock < quantity)
                    throw new IllegalStateException(warehouse.getName() + " 재고 부족 (현재: " + warehouseStock + "개)");
                product.setWarehouseStockIcheon(warehouseStock - quantity);
                break;
            case "BUCHEON":
                warehouseStock = product.getWarehouseStockBucheon();
                if (warehouseStock < quantity)
                    throw new IllegalStateException(warehouse.getName() + " 재고 부족 (현재: " + warehouseStock + "개)");
                product.setWarehouseStockBucheon(warehouseStock - quantity);
                break;
            default:
                // 신규 창고: product_warehouse_stock 테이블에서 차감
                ProductWarehouseStock ws = warehouseStockRepository
                    .findByProductIdAndWarehouseCode(product.getProductId(), warehouse.getCode())
                    .orElse(null);
                int wsStock = ws != null ? ws.getStock() : 0;
                if (wsStock < quantity)
                    throw new IllegalStateException(warehouse.getName() + " 재고 부족 (현재: " + wsStock + "개)");
                updateWarehouseStock(product.getProductId(), warehouse.getCode(),
                    warehouse.getName(), -quantity);
                log.debug("신규 창고 출고 (warehouse_stock 테이블): {} -{}", warehouse.getCode(), quantity);
        }

        // 거래 내역 기록 (총재고 변경 전에 생성해야 beforeStock이 정확함)
        String detailedNotes = String.format("창고:%s(%s) | %s",
            warehouse.getName(), warehouse.getCode(), notes != null ? notes : "");
        InventoryTransaction transaction = InventoryTransaction.createOutbound(
            product, quantity, orderId, detailedNotes);
        transactionRepository.save(transaction);

        // 총 재고 차감
        product.decreaseStock(quantity);

        Product saved = productRepository.save(product);

        log.info("✅ 출고 완료: {} - 창고:{}, 재고 {} → {}",
            product.getProductName(), warehouse.getName(),
            transaction.getBeforeStock(), transaction.getAfterStock());

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

        InventoryTransaction transaction = InventoryTransaction.createInbound(
            product, quantity, location, notes);
        transactionRepository.save(transaction);

        product.increaseStock(quantity);

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

        InventoryTransaction transaction = InventoryTransaction.createOutbound(
            product, quantity, orderId, notes);
        transactionRepository.save(transaction);

        product.decreaseStock(quantity);

        Product saved = productRepository.save(product);

        log.info("✅ 출고 완료: {} - 재고 {} → {}",
            product.getProductName(), transaction.getBeforeStock(), transaction.getAfterStock());

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

    /**
     * 신규 창고별 재고 업데이트 (ProductWarehouseStock 테이블)
     * quantity > 0 : 입고, quantity < 0 : 출고
     * 같은 트랜잭션 내에서 호출되므로 @Transactional 없음
     */
    private void updateWarehouseStock(UUID productId, String warehouseCode,
                                      String warehouseName, int quantity) {
        try {
            ProductWarehouseStock ws = warehouseStockRepository
                .findByProductIdAndWarehouseCode(productId, warehouseCode)
                .orElseGet(() -> ProductWarehouseStock.builder()
                    .productId(productId)
                    .warehouseCode(warehouseCode)
                    .warehouseName(warehouseName)
                    .stock(0)
                    .build());

            int before = ws.getStock();
            ws.setStock(before + quantity);
            warehouseStockRepository.save(ws);
            log.info("✅ 창고별 재고 업데이트: {} ({}) {} → {}",
                warehouseName, warehouseCode, before, ws.getStock());
        } catch (Exception e) {
            log.error("❌ 창고별 재고 업데이트 실패: {} ({}) - {}", warehouseName, warehouseCode, e.getMessage(), e);
        }
    }

    /**
     * 반품 취소용 강제 출고 — 재고 부족 체크 없이 차감
     */
    @Transactional
    public void forceOutboundForReturn(UUID productId, int quantity,
                                       String warehouseCode, String notes) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));

        Warehouse warehouse = warehouseRepository.findByCode(warehouseCode)
            .orElse(null);
        String warehouseName = warehouse != null ? warehouse.getName() : warehouseCode;

        switch (warehouseCode) {
            case "ANYANG":
                product.setWarehouseStockAnyang(Math.max(0, product.getWarehouseStockAnyang() - quantity));
                break;
            case "ICHEON_BOX":
            case "ICHEON_PCS":
                product.setWarehouseStockIcheon(Math.max(0, product.getWarehouseStockIcheon() - quantity));
                break;
            case "BUCHEON":
                product.setWarehouseStockBucheon(Math.max(0, product.getWarehouseStockBucheon() - quantity));
                break;
            default:
                // 신규 창고: 0 이하로 내려가지 않도록 처리
                ProductWarehouseStock ws = warehouseStockRepository
                    .findByProductIdAndWarehouseCode(productId, warehouseCode)
                    .orElseGet(() -> ProductWarehouseStock.builder()
                        .productId(productId).warehouseCode(warehouseCode)
                        .warehouseName(warehouseName).stock(0).build());
                ws.setStock(Math.max(0, ws.getStock() - quantity));
                warehouseStockRepository.save(ws);
        }

        // 거래 내역 기록 (총재고 변경 전에 생성해야 beforeStock이 정확함)
        int newTotal = Math.max(0, product.getTotalStock() - quantity);
        int newAvail = Math.max(0, product.getAvailableStock() - quantity);
        String detailedNotes = String.format("창고:%s(%s) | %s", warehouseName, warehouseCode, notes);
        InventoryTransaction transaction = InventoryTransaction.builder()
            .product(product)
            .transactionType("OUT")
            .quantity(quantity)
            .beforeStock(product.getTotalStock())
            .afterStock(newTotal)
            .fromLocation(warehouseName)
            .referenceType("RETURN_CANCEL")
            .notes(detailedNotes)
            .build();
        transactionRepository.save(transaction);

        // 총재고 차감 (0 이하 방지)
        product.setTotalStock(newTotal);
        product.setAvailableStock(newAvail);

        productRepository.save(product);
        log.info("✅ 강제 출고 완료 (반품 취소): {} - 창고:{}", product.getProductName(), warehouseName);
    }

    /**
     * 창고별 재고 조회
     */
    @Transactional(readOnly = true)
    public List<ProductWarehouseStock> getWarehouseStocks(UUID productId) {
        return warehouseStockRepository.findByProductId(productId);
    }
}
