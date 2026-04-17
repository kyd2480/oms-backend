package com.oms.collector.controller;

import com.oms.collector.dto.ClaimRequest;
import com.oms.collector.service.ClaimProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/claims")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ClaimController {
    private final ClaimProcessingService claimProcessingService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> apply(@RequestBody ClaimRequest request) {
        ClaimProcessingService.ClaimResult result = claimProcessingService.applyClaim(request);
        return ResponseEntity.ok(Map.of(
            "success", result.success(),
            "message", result.message(),
            "orderNo", result.orderNo(),
            "claimType", result.claimType(),
            "returnId", result.returnId() != null ? result.returnId() : "",
            "shippingHold", result.shippingHold()
        ));
    }

    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> applyBatch(@RequestBody List<ClaimRequest> requests) {
        List<Map<String, Object>> results = new ArrayList<>();
        int success = 0;

        for (ClaimRequest request : requests) {
            try {
                ClaimProcessingService.ClaimResult result = claimProcessingService.applyClaim(request);
                if (result.success()) {
                    success++;
                }
                results.add(Map.of(
                    "success", result.success(),
                    "message", result.message(),
                    "orderNo", result.orderNo(),
                    "claimType", result.claimType()
                ));
            } catch (Exception e) {
                results.add(Map.of(
                    "success", false,
                    "message", e.getMessage() != null ? e.getMessage() : "처리 실패"
                ));
            }
        }

        return ResponseEntity.ok(Map.of(
            "success", success == requests.size(),
            "processed", requests.size(),
            "succeeded", success,
            "failed", requests.size() - success,
            "results", results
        ));
    }
}
