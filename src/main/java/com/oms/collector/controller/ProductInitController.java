package com.oms.collector.controller;

import com.oms.collector.entity.Product;
import com.oms.collector.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 상품 초기화 Controller (CSV 업로드)
 */
@Slf4j
@RestController
@RequestMapping("/api/init")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProductInitController {

    private static final List<String> PRODUCT_CSV_HEADERS = List.of(
        "옵션코드", "공급처명", "상품명", "옵션명", "색상", "바코드", "바코드2", "카테고리", "위치", "원가", "판매가", "비고"
    );
    private static final List<String> REQUIRED_PRODUCT_CSV_HEADERS = List.of("상품명", "옵션명", "바코드");

    private final ProductRepository productRepository;

    /**
     * CSV 업로드 OPTIONS 요청 처리 (CORS Preflight)
     */
    @RequestMapping(value = "/products/upload-csv", method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> uploadCsvOptions(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "*");
        response.setHeader("Access-Control-Max-Age", "3600");
        return ResponseEntity.ok().build();
    }

    /**
     * 전체 상품 삭제 (초기화)
     */
    @DeleteMapping("/products/all")
    public ResponseEntity<String> deleteAllProducts() {
        log.warn("전체 상품 삭제 요청");

        try {
            productRepository.deleteAll();
            log.info("전체 상품 삭제 완료");
            return ResponseEntity.ok("전체 상품이 삭제되었습니다.");
        } catch (Exception e) {
            log.error("상품 삭제 실패", e);
            return ResponseEntity.internalServerError().body("삭제 실패: " + e.getMessage());
        }
    }

    /**
     * CSV 파일 업로드로 상품 등록
     */
    @PostMapping("/products/upload-csv")
    public ResponseEntity<String> uploadCsvProducts(
            @RequestParam("file") MultipartFile file,
            HttpServletResponse response) {

        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "*");

        log.info("CSV 상품 업로드 시작");
        log.info("   파일명: {}", file.getOriginalFilename());
        log.info("   파일크기: {} bytes", file.getSize());

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("파일이 비어있습니다.");
        }

        try {
            List<Product> parsedProducts = parseCsvFile(file);
            int parsedCount = parsedProducts.size();
            Map<String, Product> uniqueProductsBySku = new LinkedHashMap<>();
            int duplicateInCsvCount = 0;
            for (Product product : parsedProducts) {
                String uniqueKey = productUniqueKey(product);
                if (uniqueProductsBySku.containsKey(uniqueKey)) {
                    duplicateInCsvCount++;
                    continue;
                }
                uniqueProductsBySku.put(uniqueKey, product);
            }
            List<Product> products = new ArrayList<>(uniqueProductsBySku.values());
            log.info("CSV 파싱 완료: {}개 상품, 내부 중복 {}개 제외", parsedCount, duplicateInCsvCount);

            ExistingProductKeys existingKeys = loadExistingProductKeys(products);
            int chunkSize = 1000;
            int newCount = 0;
            int updateCount = 0;
            int totalProcessed = 0;

            for (int i = 0; i < products.size(); i += chunkSize) {
                int end = Math.min(i + chunkSize, products.size());
                List<Product> chunk = products.subList(i, end);

                List<Product> toSave = new ArrayList<>();
                for (Product product : chunk) {
                    if (isExistingProduct(product, existingKeys)) {
                        updateCount++;
                    } else {
                        toSave.add(product);
                    }
                }

                if (!toSave.isEmpty()) {
                    productRepository.saveAll(toSave);
                    newCount += toSave.size();
                }

                totalProcessed = end;
                log.info("진행: {}/{} ({}%)",
                    totalProcessed, products.size(),
                    products.isEmpty() ? 100 : (totalProcessed * 100 / products.size()));
            }

            String message = String.format(
                "CSV 업로드 완료\n신규: %,d개\n기존/건너뜀: %,d개\nCSV 내부 중복 제외: %,d개\n총 읽은 행: %,d개",
                newCount, updateCount, duplicateInCsvCount, parsedCount
            );

            log.info(message);
            return ResponseEntity.ok(message);

        } catch (IllegalArgumentException e) {
            log.warn("CSV 검증 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().body("업로드 실패: " + e.getMessage());
        } catch (Exception e) {
            log.error("CSV 처리 실패", e);
            return ResponseEntity.status(500)
                .body("업로드 실패: " + e.getMessage());
        }
    }

    private List<Product> parseCsvFile(MultipartFile file) throws Exception {
        List<Product> products = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        String content = readCsvContent(file);
        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new IllegalArgumentException("CSV 헤더가 비어있습니다.");
            }

            Map<String, Integer> headerMap = buildHeaderMap(parseCsvLine(headerLine));
            validateRequiredHeaders(headerMap);

            String line;
            int rowNo = 1;
            while ((line = reader.readLine()) != null) {
                rowNo++;
                String[] fields = parseCsvLine(line);
                if (isBlankRow(fields)) {
                    continue;
                }

                String optionCode = getField(fields, headerMap, "옵션코드");
                String vendorName = getField(fields, headerMap, "공급처명");
                String productName = getField(fields, headerMap, "상품명");
                String optionName = getField(fields, headerMap, "옵션명");
                String color = getField(fields, headerMap, "색상");
                String barcode = getField(fields, headerMap, "바코드");
                String barcode2 = getField(fields, headerMap, "바코드2");
                String category = getField(fields, headerMap, "카테고리");
                String location = getField(fields, headerMap, "위치");
                BigDecimal costPrice = parseMoney(getField(fields, headerMap, "원가"));
                BigDecimal sellingPrice = parseMoney(getField(fields, headerMap, "판매가"));
                String note = getField(fields, headerMap, "비고");

                List<String> missing = new ArrayList<>();
                if (productName.isBlank()) missing.add("상품명");
                if (optionName.isBlank()) missing.add("옵션명");
                if (barcode.isBlank()) missing.add("바코드");
                if (!missing.isEmpty()) {
                    throw new IllegalArgumentException(rowNo + "행 필수값 누락: " + String.join(", ", missing));
                }

                String sku = !optionCode.isBlank() ? optionCode : generateInternalSku();

                Product product = Product.builder()
                    .sku(sku)
                    .barcode(barcode)
                    .barcode2(barcode2)
                    .optionCode(optionCode.isBlank() ? null : optionCode)
                    .productName(productName)
                    .optionName(optionName)
                    .color(color)
                    .vendorName(vendorName)
                    .category(category)
                    .costPrice(costPrice)
                    .sellingPrice(sellingPrice)
                    .totalStock(0)
                    .availableStock(0)
                    .reservedStock(0)
                    .warehouseLocation(location)
                    .warehouseStockAnyang(0)
                    .warehouseStockIcheon(0)
                    .warehouseStockBucheon(0)
                    .isActive(true)
                    .description(optionName)
                    .note(note)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

                products.add(product);
            }
        }

        if (products.isEmpty()) {
            throw new IllegalArgumentException("등록할 상품 행이 없습니다.");
        }

        log.info("파싱 완료: {}개 상품", products.size());
        return products;
    }

    private String readCsvContent(MultipartFile file) throws Exception {
        byte[] bytes = file.getBytes();
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (!utf8.contains("\uFFFD")) {
            return utf8.replace("\uFEFF", "");
        }
        return new String(bytes, Charset.forName("EUC-KR")).replace("\uFEFF", "");
    }

    private Map<String, Integer> buildHeaderMap(String[] headers) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            String header = cleanCell(headers[i]);
            if (!header.isBlank()) {
                map.put(header, i);
            }
        }
        return map;
    }

    private void validateRequiredHeaders(Map<String, Integer> headerMap) {
        List<String> missing = new ArrayList<>();
        for (String required : REQUIRED_PRODUCT_CSV_HEADERS) {
            if (!headerMap.containsKey(required)) {
                missing.add(required);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("필수 헤더 누락: " + String.join(", ", missing)
                + " / 필요 헤더: " + String.join(", ", PRODUCT_CSV_HEADERS));
        }
    }

    private String getField(String[] fields, Map<String, Integer> headerMap, String key) {
        Integer index = headerMap.get(key);
        if (index == null || index < 0 || index >= fields.length) {
            return "";
        }
        return cleanCell(fields[index]);
    }

    private String cleanCell(String value) {
        if (value == null) return "";
        String cleaned = value.replace("\uFEFF", "").trim();
        if (cleaned.startsWith("=\"" ) && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(2, cleaned.length() - 1);
        }
        if (cleaned.length() >= 2 && cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned.trim();
    }

    private String generateInternalSku() {
        return "AUTO-" + UUID.randomUUID();
    }

    private String productUniqueKey(Product product) {
        String optionCode = product.getOptionCode();
        if (optionCode != null && !optionCode.isBlank()) {
            return "OPTION:" + optionCode.trim();
        }
        return "BARCODE:" + product.getBarcode();
    }

    private ExistingProductKeys loadExistingProductKeys(List<Product> products) {
        Set<String> lookupCodes = new HashSet<>();
        for (Product product : products) {
            String optionCode = product.getOptionCode();
            if (optionCode != null && !optionCode.isBlank()) {
                lookupCodes.add(normalizeKey(product.getSku()));
            }
            lookupCodes.add(normalizeKey(product.getBarcode()));
        }

        ExistingProductKeys keys = new ExistingProductKeys();
        List<String> codes = lookupCodes.stream()
            .filter(code -> !code.isBlank())
            .toList();

        int chunkSize = 1000;
        for (int i = 0; i < codes.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, codes.size());
            for (Product existing : productRepository.findBySkuOrBarcodeInLowercase(codes.subList(i, end))) {
                keys.skus.add(normalizeKey(existing.getSku()));
                if (Boolean.TRUE.equals(existing.getIsActive())) {
                    keys.barcodes.add(normalizeKey(existing.getBarcode()));
                    keys.barcodes.add(normalizeKey(existing.getBarcode2()));
                }
            }
        }
        return keys;
    }

    private boolean isExistingProduct(Product product, ExistingProductKeys keys) {
        String optionCode = product.getOptionCode();
        if (optionCode != null && !optionCode.isBlank() && keys.skus.contains(normalizeKey(product.getSku()))) {
            return true;
        }
        return keys.barcodes.contains(normalizeKey(product.getBarcode()));
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static class ExistingProductKeys {
        private final Set<String> skus = new HashSet<>();
        private final Set<String> barcodes = new HashSet<>();
    }

    private BigDecimal parseMoney(String value) {
        String cleaned = value == null ? "" : value.replace(",", "").trim();
        if (cleaned.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private boolean isBlankRow(String[] fields) {
        for (String field : fields) {
            if (!cleanCell(field).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
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
        log.warn("모든 상품 삭제");
        productRepository.deleteAll();
        return ResponseEntity.ok("All products deleted");
    }
}
