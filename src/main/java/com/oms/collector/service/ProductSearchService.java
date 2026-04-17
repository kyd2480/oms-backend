package com.oms.collector.service;

import com.oms.collector.entity.Product;
import com.oms.collector.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductSearchService {

    private final ProductRepository productRepository;

    public List<Product> search(String keyword, int limit) {
        String rawKeyword = keyword == null ? "" : keyword.trim();
        if (rawKeyword.isBlank()) {
            return List.of();
        }

        String normalizedKeyword = normalize(rawKeyword);
        List<String> tokens = Arrays.stream(rawKeyword.split("[\\s/|,()\\[\\]-]+"))
            .map(this::normalize)
            .filter(token -> !token.isBlank())
            .distinct()
            .toList();

        return productRepository.findByIsActiveTrueOrderByProductNameAsc().stream()
            .map(product -> new RankedProduct(product, score(product, rawKeyword, normalizedKeyword, tokens)))
            .filter(rank -> rank.score() > 0)
            .sorted(Comparator
                .comparingInt(RankedProduct::score).reversed()
                .thenComparing(rank -> safe(rank.product().getProductName())))
            .limit(Math.max(1, limit))
            .map(RankedProduct::product)
            .collect(Collectors.toList());
    }

    private int score(Product product, String rawKeyword, String normalizedKeyword, List<String> tokens) {
        String productName = safe(product.getProductName());
        String sku = safe(product.getSku());
        String barcode = safe(product.getBarcode());
        String description = safe(product.getDescription());

        String nameNorm = normalize(productName);
        String skuNorm = normalize(sku);
        String barcodeNorm = normalize(barcode);
        String descNorm = normalize(description);

        int score = 0;

        if (sku.equalsIgnoreCase(rawKeyword) || barcode.equalsIgnoreCase(rawKeyword)) score += 1000;
        if (skuNorm.equals(normalizedKeyword) || barcodeNorm.equals(normalizedKeyword)) score += 950;
        if (nameNorm.equals(normalizedKeyword)) score += 900;

        if (!skuNorm.isBlank() && skuNorm.startsWith(normalizedKeyword)) score += 700;
        if (!barcodeNorm.isBlank() && barcodeNorm.startsWith(normalizedKeyword)) score += 650;
        if (!nameNorm.isBlank() && nameNorm.startsWith(normalizedKeyword)) score += 600;

        if (!skuNorm.isBlank() && skuNorm.contains(normalizedKeyword)) score += 500;
        if (!barcodeNorm.isBlank() && barcodeNorm.contains(normalizedKeyword)) score += 450;
        if (!nameNorm.isBlank() && nameNorm.contains(normalizedKeyword)) score += 400;
        if (!descNorm.isBlank() && descNorm.contains(normalizedKeyword)) score += 150;

        for (String token : tokens) {
            if (token.isBlank()) continue;
            if (!skuNorm.isBlank() && skuNorm.contains(token)) score += 120;
            if (!barcodeNorm.isBlank() && barcodeNorm.contains(token)) score += 110;
            if (!nameNorm.isBlank() && nameNorm.contains(token)) score += 100;
            if (!descNorm.isBlank() && descNorm.contains(token)) score += 40;
        }

        return score;
    }

    private String normalize(String value) {
        return safe(value)
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^0-9a-zA-Z가-힣]", "");
    }

    private String safe(String value) {
        return Objects.toString(value, "");
    }

    private record RankedProduct(Product product, int score) {}
}
