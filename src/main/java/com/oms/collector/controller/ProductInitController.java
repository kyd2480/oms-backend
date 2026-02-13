package com.oms.collector.controller;

import com.oms.collector.entity.Product;
import com.oms.collector.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 상품 초기화 Controller (CSV 업로드)
 */
@Slf4j
@RestController
@RequestMapping("/api/init")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProductInitController {
    
    private final ProductRepository productRepository;
    
    /**
     * CSV 파일 업로드로 상품 등록 (비동기 처리)
     */
    @PostMapping("/products/upload-csv")
    public ResponseEntity<String> uploadCsvProducts(@RequestParam("file") MultipartFile file) {
        log.info("📦 CSV 상품 업로드 시작");
        log.info("   파일명: {}", file.getOriginalFilename());
        log.info("   파일크기: {} bytes", file.getSize());
        log.info("   Content-Type: {}", file.getContentType());
        
        if (file.isEmpty()) {
            log.error("❌ 파일이 비어있습니다.");
            return ResponseEntity.badRequest().body("파일이 비어있습니다.");
        }
        
        try {
            // 파일 파싱만 먼저 해서 개수 확인
            List<Product> products = parseCsvFile(file);
            int totalCount = products.size();
            
            log.info("✅ CSV 파싱 완료: {}개 상품", totalCount);
            
            // 즉시 응답 (비동기 저장은 백그라운드에서)
            String message = String.format("CSV 파일 업로드 시작 - 총 %d개 상품 처리 중...", totalCount);
            
            // 비동기로 DB 저장 (별도 스레드)
            new Thread(() -> {
                try {
                    int newCount = 0;
                    int updateCount = 0;
                    
                    // 배치 처리 (100개씩)
                    for (int i = 0; i < products.size(); i += 100) {
                        int end = Math.min(i + 100, products.size());
                        List<Product> batch = products.subList(i, end);
                        
                        for (Product product : batch) {
                            if (productRepository.existsBySku(product.getSku())) {
                                updateCount++;
                            } else {
                                productRepository.save(product);
                                newCount++;
                            }
                        }
                        
                        log.info("진행률: {}/{} ({}%)", end, products.size(), (end * 100 / products.size()));
                    }
                    
                    log.info("✅ CSV 저장 완료 - 신규: {}개, 기존: {}개, 총: {}개", newCount, updateCount, products.size());
                } catch (Exception e) {
                    log.error("❌ 백그라운드 저장 실패", e);
                }
            }).start();
            
            return ResponseEntity.accepted().body(message);
            
        } catch (Exception e) {
            log.error("❌ CSV 파싱 실패", e);
            log.error("   에러 메시지: {}", e.getMessage());
            log.error("   에러 타입: {}", e.getClass().getName());
            return ResponseEntity.badRequest().body("CSV 파싱 실패: " + e.getMessage());
        }
    }
    
    private List<Product> parseCsvFile(MultipartFile file) throws Exception {
        List<Product> products = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        
        // EUC-KR 인코딩으로 읽기
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), Charset.forName("EUC-KR")))) {
            
            // 헤더 스킵
            String headerLine = reader.readLine();
            
            String line;
            while ((line = reader.readLine()) != null) {
                // CSV 파싱 (따옴표 처리)
                String[] fields = parseCsvLine(line);
                
                if (fields.length < 6) continue; // 필수 필드 체크
                
                try {
                    // 바코드번호(서식)에서 ="" 제거
                    String barcode = fields[1].replaceAll("^=?\"\"?|\"\"?$", "").trim();
                    String productName = fields[4].trim();
                    String optionName = fields[5].trim();
                    
                    if (barcode.isEmpty() || productName.isEmpty()) continue;
                    
                    // 재고는 창고별재고-1.본사(안양) 컬럼 (인덱스 8)
                    int stock = 0;
                    if (fields.length > 8 && !fields[8].isEmpty() && !fields[8].equals("=\"\"")) {
                        try {
                            stock = Integer.parseInt(fields[8].replaceAll("\"", "").trim());
                        } catch (NumberFormatException e) {
                            // 재고 파싱 실패 시 0으로
                        }
                    }
                    
                    Product product = Product.builder()
                        .sku(barcode)
                        .barcode(barcode)
                        .productName(productName + (optionName.isEmpty() ? "" : " - " + optionName))
                        .category(fields.length > 46 ? fields[46].trim() : "")
                        .costPrice(BigDecimal.ZERO)
                        .sellingPrice(BigDecimal.ZERO)
                        .totalStock(stock)
                        .availableStock(stock)
                        .reservedStock(0)
                        .safetyStock(10)
                        .warehouseLocation(fields.length > 6 ? fields[6].replaceAll("=\"\"?", "").trim() : "")
                        .isActive(true)
                        .description(optionName)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
                    
                    products.add(product);
                    
                } catch (Exception e) {
                    log.warn("상품 파싱 실패 (line skip): {}", e.getMessage());
                }
            }
        }
        
        log.info("📊 파싱 완료: {}개 상품", products.size());
        return products;
    }
    
    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;
                field.append(c);
            } else if (c == ',' && !inQuotes) {
                fields.add(field.toString());
                field = new StringBuilder();
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString());
        
        return fields.toArray(new String[0]);
    }
    
    /**
     * 모든 상품 삭제 (테스트용)
     */
    @DeleteMapping("/products")
    public ResponseEntity<String> clearProducts() {
        log.warn("⚠️ 모든 상품 삭제");
        productRepository.deleteAll();
        return ResponseEntity.ok("All products deleted");
    }
}
