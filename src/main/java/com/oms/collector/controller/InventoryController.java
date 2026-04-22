package com.oms.collector.controller;

import com.oms.collector.dto.InventoryDto;
import com.oms.collector.dto.ProductDto;
import com.oms.collector.entity.InventoryTransaction;
import com.oms.collector.entity.Product;
import com.oms.collector.entity.Order;
import com.oms.collector.entity.OrderItem;
import com.oms.collector.repository.OrderItemRepository;
import com.oms.collector.repository.ProductRepository;
import com.oms.collector.service.InventoryService;
import com.oms.collector.service.ProductSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class InventoryController {

    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryService inventoryService;
    private final ProductSearchService productSearchService;

    @GetMapping("/products")
    public ResponseEntity<List<ProductDto>> getAllProducts() {
        List<Product> products = productRepository.findByIsActiveTrueOrderByProductNameAsc();
        return ResponseEntity.ok(products.stream().map(this::toProductDto).collect(Collectors.toList()));
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<ProductDto> getProduct(@PathVariable UUID id) {
        return productRepository.findById(id)
            .map(this::toProductDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/products/search")
    public ResponseEntity<List<ProductDto>> searchProducts(@RequestParam String keyword) {
        log.info("🔍 상품 검색: {}", keyword);
        List<Product> products = productSearchService.search(keyword, 100);
        log.info("✅ 검색 결과: {}개", products.size());
        return ResponseEntity.ok(products.stream().map(this::toProductDto).collect(Collectors.toList()));
    }

    @PostMapping("/products")
    public ResponseEntity<ProductDto> createProduct(@RequestBody ProductDto dto) {
        log.info("🆕 상품 등록: {}", dto.getProductName());
        if (productRepository.existsBySku(dto.getSku())) {
            return ResponseEntity.badRequest().build();
        }
        Product product = Product.builder()
            .sku(dto.getSku())
            .productName(dto.getProductName())
            .barcode(dto.getBarcode())
            .optionCode(dto.getOptionCode() != null ? dto.getOptionCode() : dto.getSku())
            .optionName(dto.getOptionName())
            .vendorName(dto.getVendorName())
            .category(dto.getCategory())
            .costPrice(dto.getCostPrice())
            .sellingPrice(dto.getSellingPrice())
            .totalStock(0)
            .availableStock(0)
            .reservedStock(0)
            .warehouseLocation(dto.getWarehouseLocation())
            .isActive(true)
            .description(dto.getDescription())
            .note(dto.getNote())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
        Product saved = productRepository.save(product);
        log.info("✅ 상품 등록 완료: {} (SKU: {})", saved.getProductName(), saved.getSku());
        return ResponseEntity.ok(toProductDto(saved));
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<ProductDto> updateProduct(@PathVariable UUID id, @RequestBody ProductDto dto) {
        log.info("✏️ 상품 수정: {}", id);
        return productRepository.findById(id)
            .map(product -> {
                product.setProductName(dto.getProductName());
                product.setBarcode(dto.getBarcode());
                product.setOptionCode(dto.getOptionCode() != null ? dto.getOptionCode() : product.getOptionCode());
                product.setOptionName(dto.getOptionName());
                product.setVendorName(dto.getVendorName());
                product.setCategory(dto.getCategory());
                product.setCostPrice(dto.getCostPrice());
                product.setSellingPrice(dto.getSellingPrice());
                product.setWarehouseLocation(dto.getWarehouseLocation());
                product.setDescription(dto.getDescription());
                product.setNote(dto.getNote());
                product.setUpdatedAt(LocalDateTime.now());
                return ResponseEntity.ok(toProductDto(productRepository.save(product)));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/products/{id}/stock")
    public ResponseEntity<?> updateProductStock(@PathVariable UUID id,
                                                @RequestBody InventoryDto.StockUpdateRequest request) {
        log.info("✏️ 재고현황 수량 수정: product={}, warehouse={}", id, request.getWarehouseCode());
        try {
            Product product = inventoryService.updateInventorySnapshot(
                id,
                request.getWarehouseCode(),
                request.getWarehouseName(),
                request.getWarehouseStock(),
                request.getReservedStock(),
                request.getWarehouseLocation(),
                request.getNote(),
                request.getReason()
            );
            return ResponseEntity.ok(toProductDto(product));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID id) {
        log.info("🗑️ 상품 삭제: {}", id);
        return productRepository.findById(id)
            .map(product -> {
                product.setIsActive(false);
                productRepository.save(product);
                return ResponseEntity.noContent().<Void>build();
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/inbound-warehouse")
    public ResponseEntity<?> processInboundWarehouse(@RequestBody InventoryDto.InboundWarehouseRequest request) {
        log.info("📦 입고 처리 요청 (창고별): 상품 ID={}, 수량={}, 창고={}",
            request.getProductId(), request.getQuantity(), request.getWarehouse());
        try {
            Product product = inventoryService.processInboundWithWarehouse(
                request.getProductId(), request.getQuantity(),
                request.getWarehouse(), request.getLocation(), request.getNotes());
            return ResponseEntity.ok(toProductDto(product));
        } catch (IllegalArgumentException e) {
            log.error("❌ 입고 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/outbound-warehouse")
    public ResponseEntity<?> processOutboundWarehouse(@RequestBody InventoryDto.OutboundWarehouseRequest request) {
        log.info("📤 출고 처리 요청 (창고별): 상품 ID={}, 수량={}, 창고={}",
            request.getProductId(), request.getQuantity(), request.getWarehouse());
        try {
            Product product = inventoryService.processOutboundWithWarehouse(
                request.getProductId(), request.getQuantity(),
                request.getWarehouse(), request.getOrderId(), request.getNotes());
            return ResponseEntity.ok(toProductDto(product));
        } catch (IllegalStateException | IllegalArgumentException e) {
            log.error("❌ 출고 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/inbound")
    public ResponseEntity<ProductDto> processInbound(@RequestBody InventoryDto.InboundRequest request) {
        log.info("📦 입고 처리 요청: 상품 ID={}, 수량={}", request.getProductId(), request.getQuantity());
        Product product = inventoryService.processInbound(
            request.getProductId(), request.getQuantity(),
            request.getLocation(), request.getNotes());
        return ResponseEntity.ok(toProductDto(product));
    }

    @PostMapping("/outbound")
    public ResponseEntity<ProductDto> processOutbound(@RequestBody InventoryDto.OutboundRequest request) {
        log.info("📤 출고 처리 요청: 상품 ID={}, 수량={}", request.getProductId(), request.getQuantity());
        try {
            Product product = inventoryService.processOutbound(
                request.getProductId(), request.getQuantity(),
                request.getOrderId(), request.getNotes());
            return ResponseEntity.ok(toProductDto(product));
        } catch (IllegalStateException e) {
            log.error("❌ 출고 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/adjust")
    public ResponseEntity<ProductDto> adjustInventory(@RequestBody InventoryDto.AdjustRequest request) {
        log.info("🔧 재고 조정 요청: 상품 ID={}, 수량={}", request.getProductId(), request.getQuantity());
        Product product = inventoryService.adjustInventory(
            request.getProductId(), request.getQuantity(), request.getReason());
        return ResponseEntity.ok(toProductDto(product));
    }

    /** 재고 없는 상품 조회 (low-stock → out-of-stock으로 대체) */
    @GetMapping("/low-stock")
    public ResponseEntity<List<ProductDto>> getLowStockProducts() {
        List<Product> products = inventoryService.getOutOfStockProducts();
        return ResponseEntity.ok(products.stream().map(this::toProductDto).collect(Collectors.toList()));
    }

    @GetMapping("/out-of-stock")
    public ResponseEntity<List<ProductDto>> getOutOfStockProducts() {
        List<Product> products = inventoryService.getOutOfStockProducts();
        return ResponseEntity.ok(products.stream().map(this::toProductDto).collect(Collectors.toList()));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<InventoryDto.TransactionResponse>> getAllTransactions(
            @RequestParam(required = false, defaultValue = "100") int limit) {
        log.info("📋 전체 거래 내역 조회: limit={}", limit);
        List<InventoryTransaction> transactions = inventoryService.getRecentTransactions(limit);
        List<InventoryDto.TransactionResponse> dtos = transactions.stream()
            .map(this::toTransactionDto).collect(Collectors.toList());
        log.info("✅ 거래 내역 {}건 조회", dtos.size());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/transactions/search")
    public ResponseEntity<List<InventoryDto.TransactionResponse>> searchTransactions(
            @RequestParam String keyword,
            @RequestParam(required = false, defaultValue = "100") int limit) {
        log.info("🔍 거래 내역 검색: keyword={}, limit={}", keyword, limit);
        List<InventoryTransaction> transactions = inventoryService.searchTransactions(keyword, limit);
        List<InventoryDto.TransactionResponse> dtos = transactions.stream()
            .map(this::toTransactionDto).collect(Collectors.toList());
        log.info("✅ 검색 결과 {}건", dtos.size());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/products/{id}/transactions")
    public ResponseEntity<List<InventoryDto.TransactionResponse>> getTransactionHistory(
            @PathVariable UUID id,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        LocalDateTime start = startDate != null ? LocalDateTime.parse(startDate) : LocalDateTime.now().minusMonths(1);
        LocalDateTime end   = endDate   != null ? LocalDateTime.parse(endDate)   : LocalDateTime.now();
        List<InventoryTransaction> transactions = (startDate == null && endDate == null)
            ? inventoryService.getAllTransactionHistory(id)
            : inventoryService.getTransactionHistory(id, start, end);
        return ResponseEntity.ok(transactions.stream().map(this::toTransactionDto).collect(Collectors.toList()));
    }

    @GetMapping("/products/{id}/unshipped-items")
    public ResponseEntity<List<InventoryDto.UnshippedOrderItemResponse>> getUnshippedItems(@PathVariable UUID id) {
        return productRepository.findById(id)
            .map(product -> {
                Set<String> codes = new LinkedHashSet<>();
                addIfPresent(codes, product.getSku());
                addIfPresent(codes, product.getBarcode());
                addIfPresent(codes, product.getOptionCode());
                if (codes.isEmpty()) {
                    return ResponseEntity.ok(List.<InventoryDto.UnshippedOrderItemResponse>of());
                }
                List<OrderItem> items = orderItemRepository.findUnshippedByProductCodes(
                    new ArrayList<>(codes),
                    List.of(Order.OrderStatus.PENDING, Order.OrderStatus.CONFIRMED)
                );
                return ResponseEntity.ok(items.stream().map(this::toUnshippedDto).collect(Collectors.toList()));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 재고 동기화: 레거시 창고 컬럼 기준으로 totalStock/availableStock 재계산
     * product_warehouse_stock 테이블의 레거시 창고(ANYANG/ICHEON_BOX/ICHEON_PCS/BUCHEON) 항목은 삭제 후 정리
     */
    @PostMapping("/products/{id}/sync-stock")
    public ResponseEntity<?> syncStock(@PathVariable UUID id) {
        log.info("🔄 재고 동기화 요청: {}", id);
        return productRepository.findById(id).map(product -> {
            // 1. product_warehouse_stock 테이블에서 레거시 창고 항목 제거 (중복 방지)
            for (String legacyCode : new String[]{"ANYANG", "ICHEON_BOX", "ICHEON_PCS", "BUCHEON"}) {
                inventoryService.deleteWarehouseStockIfExists(product.getProductId(), legacyCode);
            }

            // 2. 레거시 컬럼 합산 = 정상 창고 재고
            int legacySum = (product.getWarehouseStockAnyang()  != null ? product.getWarehouseStockAnyang()  : 0)
                          + (product.getWarehouseStockIcheon()  != null ? product.getWarehouseStockIcheon()  : 0)
                          + (product.getWarehouseStockBucheon() != null ? product.getWarehouseStockBucheon() : 0);

            // 3. totalStock / availableStock 재설정 (reservedStock 유지)
            int reserved = product.getReservedStock() != null ? product.getReservedStock() : 0;
            product.setTotalStock(legacySum);
            product.setAvailableStock(Math.max(0, legacySum - reserved));
            productRepository.save(product);

            log.info("✅ 재고 동기화 완료: {} → totalStock={}", product.getProductName(), legacySum);
            return ResponseEntity.ok(toProductDto(product));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/stats")
    public ResponseEntity<InventoryDto.StatsResponse> getInventoryStats() {
        List<Product> allProducts  = productRepository.findByIsActiveTrueOrderByProductNameAsc();
        List<Product> outOfStock   = inventoryService.getOutOfStockProducts();
        int totalValue = allProducts.stream()
            .mapToInt(p -> (p.getCostPrice() != null ? p.getCostPrice().intValue() : 0) * p.getTotalStock())
            .sum();
        InventoryDto.StatsResponse stats = InventoryDto.StatsResponse.builder()
            .totalProducts(allProducts.size())
            .totalStockValue(totalValue)
            .lowStockCount(0)  // 안전재고 제거 → 항상 0
            .outOfStockCount(outOfStock.size())
            .build();
        return ResponseEntity.ok(stats);
    }

    private ProductDto toProductDto(Product product) {
        return ProductDto.builder()
            .productId(product.getProductId())
            .sku(product.getSku())
            .productName(product.getProductName())
            .barcode(product.getBarcode())
            .optionCode(product.getOptionCode())
            .optionName(product.getOptionName())
            .vendorName(product.getVendorName())
            .category(product.getCategory())
            .costPrice(product.getCostPrice())
            .sellingPrice(product.getSellingPrice())
            .totalStock(product.getTotalStock())
            .availableStock(product.getAvailableStock())
            .reservedStock(product.getReservedStock())
            .warehouseLocation(product.getWarehouseLocation())
            .warehouseStockAnyang(product.getWarehouseStockAnyang())
            .warehouseStockIcheon(product.getWarehouseStockIcheon())
            .warehouseStockBucheon(product.getWarehouseStockBucheon())
            .isActive(product.getIsActive())
            .description(product.getDescription())
            .note(product.getNote())
            .isOutOfStock(product.isOutOfStock())
            .createdAt(product.getCreatedAt())
            .updatedAt(product.getUpdatedAt())
            .build();
    }

    private InventoryDto.TransactionResponse toTransactionDto(InventoryTransaction transaction) {
        Product product = transaction.getProduct();
        return InventoryDto.TransactionResponse.builder()
            .transactionId(transaction.getTransactionId())
            .productId(product != null ? product.getProductId() : null)
            .productName(product != null ? product.getProductName() : "알 수 없음")
            .sku(product != null ? product.getSku() : "N/A")
            .transactionType(transaction.getTransactionType())
            .quantity(transaction.getQuantity())
            .beforeStock(transaction.getBeforeStock())
            .afterStock(transaction.getAfterStock())
            .fromLocation(transaction.getFromLocation())
            .toLocation(transaction.getToLocation())
            .referenceType(transaction.getReferenceType())
            .notes(transaction.getNotes())
            .createdBy(transaction.getCreatedBy())
            .createdAt(transaction.getCreatedAt())
            .build();
    }

    private InventoryDto.UnshippedOrderItemResponse toUnshippedDto(OrderItem item) {
        Order order = item.getOrder();
        return InventoryDto.UnshippedOrderItemResponse.builder()
            .orderNo(order != null ? order.getOrderNo() : "")
            .orderedAt(order != null && order.getOrderedAt() != null ? order.getOrderedAt().toString() : "")
            .salesChannel(order != null && order.getChannel() != null ? order.getChannel().getChannelName() : "")
            .channelOrderNo(order != null ? order.getChannelOrderNo() : "")
            .productName(item.getProductName())
            .optionName(item.getOptionName())
            .productCode(item.getProductCode())
            .recipientName(order != null ? order.getRecipientName() : "")
            .customerName(order != null ? order.getCustomerName() : "")
            .invoiceNo(extractInvoiceNo(order != null ? order.getDeliveryMemo() : null))
            .shipAvailability(order != null && order.getOrderStatus() == Order.OrderStatus.CONFIRMED ? "출고가능" : "출고대기")
            .quantity(item.getActiveQuantity())
            .build();
    }

    private static void addIfPresent(Set<String> target, String value) {
        if (value != null && !value.isBlank()) {
            target.add(value.trim());
        }
    }

    private static String extractInvoiceNo(String memo) {
        if (memo == null || memo.isBlank()) return "";
        int idx = memo.indexOf("TRACKING:");
        if (idx < 0) return "";
        String part = memo.substring(idx + "TRACKING:".length());
        int end = part.indexOf('|');
        return end >= 0 ? part.substring(0, end) : part;
    }
}
