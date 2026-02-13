package com.oms.collector.controller;

import com.oms.collector.dto.InventoryDto;
import com.oms.collector.dto.ProductDto;
import com.oms.collector.entity.InventoryTransaction;
import com.oms.collector.entity.Product;
import com.oms.collector.repository.ProductRepository;
import com.oms.collector.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 재고 관리 API Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class InventoryController {
    
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    
    /**
     * 전체 상품 목록 조회
     */
    @GetMapping("/products")
    public ResponseEntity<List<ProductDto>> getAllProducts() {
        List<Product> products = productRepository.findByIsActiveTrueOrderByProductNameAsc();
        
        List<ProductDto> dtos = products.stream()
            .map(this::toProductDto)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }
    
    /**
     * 상품 상세 조회
     */
    @GetMapping("/products/{id}")
    public ResponseEntity<ProductDto> getProduct(@PathVariable UUID id) {
        return productRepository.findById(id)
            .map(this::toProductDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * 상품 검색
     */
    @GetMapping("/products/search")
    public ResponseEntity<List<ProductDto>> searchProducts(@RequestParam String keyword) {
        List<Product> products = productRepository.findByProductNameContainingIgnoreCaseAndIsActiveTrue(keyword);
        
        List<ProductDto> dtos = products.stream()
            .map(this::toProductDto)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }
    
    /**
     * 상품 등록
     */
    @PostMapping("/products")
    public ResponseEntity<ProductDto> createProduct(@RequestBody ProductDto dto) {
        log.info("🆕 상품 등록: {}", dto.getProductName());
        
        // SKU 중복 체크
        if (productRepository.existsBySku(dto.getSku())) {
            return ResponseEntity.badRequest().build();
        }
        
        Product product = Product.builder()
            .sku(dto.getSku())
            .productName(dto.getProductName())
            .barcode(dto.getBarcode())
            .category(dto.getCategory())
            .costPrice(dto.getCostPrice())
            .sellingPrice(dto.getSellingPrice())
            .totalStock(0)
            .availableStock(0)
            .reservedStock(0)
            .safetyStock(dto.getSafetyStock() != null ? dto.getSafetyStock() : 10)
            .warehouseLocation(dto.getWarehouseLocation())
            .isActive(true)
            .description(dto.getDescription())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
        
        Product saved = productRepository.save(product);
        
        log.info("✅ 상품 등록 완료: {} (SKU: {})", saved.getProductName(), saved.getSku());
        
        return ResponseEntity.ok(toProductDto(saved));
    }
    
    /**
     * 상품 수정
     */
    @PutMapping("/products/{id}")
    public ResponseEntity<ProductDto> updateProduct(
            @PathVariable UUID id,
            @RequestBody ProductDto dto) {
        
        log.info("✏️ 상품 수정: {}", id);
        
        return productRepository.findById(id)
            .map(product -> {
                product.setProductName(dto.getProductName());
                product.setBarcode(dto.getBarcode());
                product.setCategory(dto.getCategory());
                product.setCostPrice(dto.getCostPrice());
                product.setSellingPrice(dto.getSellingPrice());
                product.setSafetyStock(dto.getSafetyStock());
                product.setWarehouseLocation(dto.getWarehouseLocation());
                product.setDescription(dto.getDescription());
                product.setUpdatedAt(LocalDateTime.now());
                
                Product updated = productRepository.save(product);
                return ResponseEntity.ok(toProductDto(updated));
            })
            .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * 상품 삭제 (비활성화)
     */
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
    
    /**
     * 입고 처리
     */
    @PostMapping("/inbound")
    public ResponseEntity<ProductDto> processInbound(@RequestBody InventoryDto.InboundRequest request) {
        log.info("📦 입고 처리 요청: 상품 ID={}, 수량={}", request.getProductId(), request.getQuantity());
        
        Product product = inventoryService.processInbound(
            request.getProductId(),
            request.getQuantity(),
            request.getLocation(),
            request.getNotes()
        );
        
        return ResponseEntity.ok(toProductDto(product));
    }
    
    /**
     * 출고 처리
     */
    @PostMapping("/outbound")
    public ResponseEntity<ProductDto> processOutbound(@RequestBody InventoryDto.OutboundRequest request) {
        log.info("📤 출고 처리 요청: 상품 ID={}, 수량={}", request.getProductId(), request.getQuantity());
        
        try {
            Product product = inventoryService.processOutbound(
                request.getProductId(),
                request.getQuantity(),
                request.getOrderId(),
                request.getNotes()
            );
            
            return ResponseEntity.ok(toProductDto(product));
        } catch (IllegalStateException e) {
            log.error("❌ 출고 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 재고 조정
     */
    @PostMapping("/adjust")
    public ResponseEntity<ProductDto> adjustInventory(@RequestBody InventoryDto.AdjustRequest request) {
        log.info("🔧 재고 조정 요청: 상품 ID={}, 수량={}", request.getProductId(), request.getQuantity());
        
        Product product = inventoryService.adjustInventory(
            request.getProductId(),
            request.getQuantity(),
            request.getReason()
        );
        
        return ResponseEntity.ok(toProductDto(product));
    }
    
    /**
     * 안전 재고 미달 상품 조회
     */
    @GetMapping("/low-stock")
    public ResponseEntity<List<ProductDto>> getLowStockProducts() {
        List<Product> products = inventoryService.getLowStockProducts();
        
        List<ProductDto> dtos = products.stream()
            .map(this::toProductDto)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }
    
    /**
     * 재고 없는 상품 조회
     */
    @GetMapping("/out-of-stock")
    public ResponseEntity<List<ProductDto>> getOutOfStockProducts() {
        List<Product> products = inventoryService.getOutOfStockProducts();
        
        List<ProductDto> dtos = products.stream()
            .map(this::toProductDto)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }
    
    /**
     * 재고 거래 내역 조회
     */
    @GetMapping("/products/{id}/transactions")
    public ResponseEntity<List<InventoryDto.TransactionResponse>> getTransactionHistory(
            @PathVariable UUID id,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        
        LocalDateTime start = startDate != null ? 
            LocalDateTime.parse(startDate) : LocalDateTime.now().minusMonths(1);
        LocalDateTime end = endDate != null ? 
            LocalDateTime.parse(endDate) : LocalDateTime.now();
        
        List<InventoryTransaction> transactions = inventoryService.getTransactionHistory(id, start, end);
        
        List<InventoryDto.TransactionResponse> dtos = transactions.stream()
            .map(this::toTransactionDto)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }
    
    /**
     * 재고 통계
     */
    @GetMapping("/stats")
    public ResponseEntity<InventoryDto.StatsResponse> getInventoryStats() {
        List<Product> allProducts = productRepository.findByIsActiveTrueOrderByProductNameAsc();
        List<Product> lowStock = inventoryService.getLowStockProducts();
        List<Product> outOfStock = inventoryService.getOutOfStockProducts();
        
        int totalValue = allProducts.stream()
            .mapToInt(p -> (p.getCostPrice() != null ? p.getCostPrice().intValue() : 0) * p.getTotalStock())
            .sum();
        
        InventoryDto.StatsResponse stats = InventoryDto.StatsResponse.builder()
            .totalProducts(allProducts.size())
            .totalStockValue(totalValue)
            .lowStockCount(lowStock.size())
            .outOfStockCount(outOfStock.size())
            .build();
        
        return ResponseEntity.ok(stats);
    }
    
    // DTO 변환 메서드
    
    private ProductDto toProductDto(Product product) {
        return ProductDto.builder()
            .productId(product.getProductId())
            .sku(product.getSku())
            .productName(product.getProductName())
            .barcode(product.getBarcode())
            .category(product.getCategory())
            .costPrice(product.getCostPrice())
            .sellingPrice(product.getSellingPrice())
            .totalStock(product.getTotalStock())
            .availableStock(product.getAvailableStock())
            .reservedStock(product.getReservedStock())
            .safetyStock(product.getSafetyStock())
            .warehouseLocation(product.getWarehouseLocation())
            .isActive(product.getIsActive())
            .description(product.getDescription())
            .isBelowSafetyStock(product.isBelowSafetyStock())
            .isOutOfStock(product.isOutOfStock())
            .createdAt(product.getCreatedAt())
            .updatedAt(product.getUpdatedAt())
            .build();
    }
    
    private InventoryDto.TransactionResponse toTransactionDto(InventoryTransaction transaction) {
        return InventoryDto.TransactionResponse.builder()
            .transactionId(transaction.getTransactionId())
            .productId(transaction.getProduct().getProductId())
            .productName(transaction.getProduct().getProductName())
            .sku(transaction.getProduct().getSku())
            .transactionType(transaction.getTransactionType())
            .quantity(transaction.getQuantity())
            .beforeStock(transaction.getBeforeStock())
            .afterStock(transaction.getAfterStock())
            .fromLocation(transaction.getFromLocation())
            .toLocation(transaction.getToLocation())
            .notes(transaction.getNotes())
            .createdBy(transaction.getCreatedBy())
            .createdAt(transaction.getCreatedAt())
            .build();
    }
}
