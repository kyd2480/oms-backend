package com.oms.collector.controller;

import com.oms.collector.entity.Product;
import com.oms.collector.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    private static final int BATCH_SIZE = 1000;
    
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
        
        List<String> failedBarcodes = new ArrayList<>();
        
        try {
            Map<String, String> locationsByBarcode = parseRows(file, failedBarcodes);
            Map<String, Product> productsByBarcode = loadProductsByBarcode(new ArrayList<>(locationsByBarcode.keySet()));
            List<Product> updates = new ArrayList<>();

            for (Map.Entry<String, String> entry : locationsByBarcode.entrySet()) {
                Product product = productsByBarcode.get(normalize(entry.getKey()));
                if (product == null) {
                    failedBarcodes.add(entry.getKey());
                    continue;
                }
                product.setWarehouseLocation(entry.getValue());
                updates.add(product);
            }

            for (int i = 0; i < updates.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, updates.size());
                productRepository.saveAll(updates.subList(i, end));
                log.info("상품 위치 배치 저장: {}/{}", end, updates.size());
            }

            int successCount = updates.size();
            int failCount = failedBarcodes.size();
            
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

    private Map<String, String> parseRows(MultipartFile file, List<String> failures) throws Exception {
        Map<String, String> rows = new LinkedHashMap<>();
        String content = readCsvContent(file);
        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1 || line.isBlank()) continue;

                String[] fields = parseCsvLine(line);
                if (fields.length < 2) {
                    failures.add(lineNumber + "행(형식 오류)");
                    continue;
                }
                String barcode = cleanField(fields[0]);
                String location = cleanField(fields[1]);
                if (barcode.isBlank()) {
                    failures.add(lineNumber + "행(바코드 없음)");
                    continue;
                }
                rows.put(barcode, location);
            }
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("처리할 CSV 데이터가 없습니다.");
        }
        return rows;
    }

    private Map<String, Product> loadProductsByBarcode(List<String> barcodes) {
        Map<String, Product> products = new HashMap<>();
        List<String> normalized = barcodes.stream().map(this::normalize).distinct().toList();
        for (int i = 0; i < normalized.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, normalized.size());
            for (Product product : productRepository.findBySkuOrBarcodeInLowercase(normalized.subList(i, end))) {
                if (product.getBarcode() != null) products.put(normalize(product.getBarcode()), product);
                if (product.getBarcode2() != null) products.put(normalize(product.getBarcode2()), product);
            }
        }
        return products;
    }

    private String readCsvContent(MultipartFile file) throws Exception {
        byte[] bytes = file.getBytes();
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (!utf8.contains("\uFFFD")) return utf8.replace("\uFEFF", "");
        return new String(bytes, Charset.forName("EUC-KR")).replace("\uFEFF", "");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
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
