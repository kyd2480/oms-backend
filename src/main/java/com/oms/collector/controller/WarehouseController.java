package com.oms.collector.controller;

import com.oms.collector.dto.WarehouseDto;
import com.oms.collector.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 창고 관리 API
 *
 * GET    /api/warehouses          - 전체 창고 목록
 * GET    /api/warehouses/active   - 활성 창고만
 * GET    /api/warehouses/search   - 검색
 * GET    /api/warehouses/{id}     - 단건 조회
 * POST   /api/warehouses          - 생성
 * PUT    /api/warehouses/{id}     - 수정 (이름/유형/비고)
 * PATCH  /api/warehouses/{id}/toggle - 활성/비활성 토글
 * DELETE /api/warehouses/{id}     - 삭제
 */
@Slf4j
@RestController
@RequestMapping("/api/warehouses")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class WarehouseController {

    private final WarehouseService warehouseService;

    @GetMapping
    public ResponseEntity<List<WarehouseDto.Response>> getAll() {
        return ResponseEntity.ok(warehouseService.getAll());
    }

    @GetMapping("/active")
    public ResponseEntity<List<WarehouseDto.Response>> getActive() {
        return ResponseEntity.ok(warehouseService.getActive());
    }

    @GetMapping("/search")
    public ResponseEntity<List<WarehouseDto.Response>> search(@RequestParam String keyword) {
        return ResponseEntity.ok(warehouseService.search(keyword));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WarehouseDto.Response> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(warehouseService.getById(id));
    }

    @PostMapping
    public ResponseEntity<WarehouseDto.Response> create(@RequestBody WarehouseDto.CreateRequest req) {
        log.info("창고 생성 요청: {}", req.getCode());
        return ResponseEntity.ok(warehouseService.create(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WarehouseDto.Response> update(
            @PathVariable UUID id,
            @RequestBody WarehouseDto.UpdateRequest req) {
        log.info("창고 수정 요청: {}", id);
        return ResponseEntity.ok(warehouseService.update(id, req));
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<WarehouseDto.Response> toggleActive(@PathVariable UUID id) {
        log.info("창고 상태 토글: {}", id);
        return ResponseEntity.ok(warehouseService.toggleActive(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        log.info("창고 삭제 요청: {}", id);
        warehouseService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
