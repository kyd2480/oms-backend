package com.oms.collector.controller;

import com.oms.collector.service.SabangnetOrderCollectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/sabangnet/orders")
@RequiredArgsConstructor
public class SabangnetOrderController {

    private final SabangnetOrderCollectionService collectionService;

    @PostMapping("/collect")
    public ResponseEntity<SabangnetOrderCollectionService.SabangnetCollectResult> collect(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        SabangnetOrderCollectionService.SabangnetCollectResult result = collectionService.collect(startDate, endDate);
        return result.success() ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    @PostMapping("/collect/{integrationId}")
    public ResponseEntity<SabangnetOrderCollectionService.SabangnetCollectResult> collectOne(
        @PathVariable UUID integrationId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        SabangnetOrderCollectionService.SabangnetCollectResult result = collectionService.collect(integrationId, startDate, endDate);
        return result.success() ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }
}
