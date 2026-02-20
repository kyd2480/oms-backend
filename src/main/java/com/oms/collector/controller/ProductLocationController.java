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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 상품 위치 업데이트 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProductLocationController {
    
    private final ProductRepository productRepository;
    
    /**
     * CSV로 상품 위치 일괄 업데이트
     */
    @PostMapping("/update-location")
    public ResponseEntity<Map<String, Object>> updateLocationByCsv(@RequestParam("file") MultipartFile file) {
        log.info("📍 상품 위치 업데이트 시작");
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "파일이 비어있습니다."));
        }
        
        int successCount = 0;
        int failCount = 0;
        List<String> failedBarcodes = new ArrayList<>();
        
        try {
            // CSV 파일 읽기 (EUC-KR 인코딩)
            Charset charset = Charset.forName("EUC-KR");
            BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), charset));
            
            String line;
            int lineNumber = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                
                // 헤더 행 스킵 (첫 줄)
                if (lineNumber == 1) {
                    continue;
                }
                
                // 빈 줄 스킵
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                try {
                    // CSV 파싱
                    String[] fields = parseCsvLine(line);
                    
                    if (fields.length < 2) {
                        log.warn("라인 {}: 필드 부족 - {}", lineNumber, line);
                        continue;
                    }
                    
                    // A열: 바코드, B열: 위치
                    String barcode = cleanField(fields[0]);
                    String location = cleanField(fields[1]);
                    
                    if (barcode.isEmpty()) {
                        log.warn("라인 {}: 바코드 없음", lineNumber);
                        continue;
                    }
                    
                    // 바코드로 상품 검색
                    Product product = productRepository.findByBarcode(barcode).orElse(null);
                    
                    if (product == null) {
                        log.warn("라인 {}: 바코드 [{}] 상품 없음", lineNumber, barcode);
                        failCount++;
                        failedBarcodes.add(barcode);
                        continue;
                    }
                    
                    // 위치 업데이트
                    product.setWarehouseLocation(location);
                    productRepository.save(product);
                    
                    successCount++;
                    
                    if (successCount % 100 == 0) {
                        log.info("진행: {}개 완료", successCount);
                    }
                    
                } catch (Exception e) {
                    log.error("라인 {} 처리 실패: {}", lineNumber, e.getMessage());
                    failCount++;
                }
            }
            
            reader.close();
            
            log.info("✅ 위치 업데이트 완료 - 성공: {}개, 실패: {}개", successCount, failCount);
            
            // 결과 반환
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("successCount", successCount);
            result.put("failCount", failCount);
            result.put("failedBarcodes", failedBarcodes);
            result.put("message", String.format("✅ 위치 업데이트 완료\n성공: %d개\n실패: %d개", successCount, failCount));
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("❌ CSV 처리 실패", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "처리 실패: " + e.getMessage()));
        }
    }
    
    /**
     * CSV 라인 파싱 (따옴표 처리)
     */
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
     * 필드 정리 (따옴표 제거, 트림)
     */
    private String cleanField(String field) {
        if (field == null) return "";
        return field.replaceAll("^=?\"\"?|\"\"?$", "").trim();
    }
    
    /**
     * OPTIONS 요청 처리 (CORS)
     */
    @RequestMapping(value = "/update-location", method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> updateLocationOptions() {
        return ResponseEntity.ok().build();
    }
}
