package com.oms.collector.controller;

import com.oms.collector.dto.PrintTypeDto;
import com.oms.collector.service.PrintTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/print-types")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class PrintTypeController {
    private final PrintTypeService printTypeService;

    @GetMapping
    public ResponseEntity<List<PrintTypeDto.Response>> getAll() {
        return ResponseEntity.ok(printTypeService.getAll());
    }

    @GetMapping("/active")
    public ResponseEntity<List<PrintTypeDto.Response>> getActive() {
        return ResponseEntity.ok(printTypeService.getActive());
    }

    @GetMapping("/search")
    public ResponseEntity<List<PrintTypeDto.Response>> search(@RequestParam String keyword) {
        return ResponseEntity.ok(printTypeService.search(keyword));
    }

    @PostMapping
    public ResponseEntity<PrintTypeDto.Response> create(@RequestBody PrintTypeDto.CreateRequest req) {
        return ResponseEntity.ok(printTypeService.create(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PrintTypeDto.Response> update(@PathVariable UUID id, @RequestBody PrintTypeDto.UpdateRequest req) {
        return ResponseEntity.ok(printTypeService.update(id, req));
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<PrintTypeDto.Response> toggleActive(@PathVariable UUID id) {
        return ResponseEntity.ok(printTypeService.toggleActive(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        printTypeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
