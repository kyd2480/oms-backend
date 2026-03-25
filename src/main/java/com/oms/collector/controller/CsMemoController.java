package com.oms.collector.controller;

import com.oms.collector.entity.CsMemo;
import com.oms.collector.repository.CsMemoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CS 메모 API
 *
 * GET  /api/cs-memos/{orderNo}  - 주문번호로 메모 목록 조회
 * POST /api/cs-memos            - 메모 저장
 * DELETE /api/cs-memos/{memoId} - 메모 삭제
 */
@Slf4j
@RestController
@RequestMapping("/api/cs-memos")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CsMemoController {

    private final CsMemoRepository csMemoRepository;

    public static class MemoDTO {
        public String memoId;
        public String orderNo;
        public String csType;
        public String csDept;
        public String csKind;
        public String content;
        public String status;
        public String writer;
        public String createdAt;

        public MemoDTO(CsMemo m) {
            this.memoId    = m.getMemoId().toString();
            this.orderNo   = m.getOrderNo();
            this.csType    = m.getCsType();
            this.csDept    = m.getCsDept();
            this.csKind    = m.getCsKind();
            this.content   = m.getContent();
            this.status    = m.getStatus();
            this.writer    = m.getWriter();
            this.createdAt = m.getCreatedAt() != null ? m.getCreatedAt().toString() : null;
        }
    }

    public static class MemoCreateRequest {
        public String orderNo;
        public String csType;
        public String csDept;
        public String csKind;
        public String content;
        public String status;
        public String writer;
    }

    // 주문번호로 메모 목록 조회
    @GetMapping("/{orderNo}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<MemoDTO>> list(@PathVariable String orderNo) {
        return ResponseEntity.ok(
            csMemoRepository.findByOrderNoOrderByCreatedAtAsc(orderNo)
                .stream().map(MemoDTO::new).collect(Collectors.toList())
        );
    }

    // 메모 저장
    @PostMapping
    @Transactional
    public ResponseEntity<MemoDTO> save(@RequestBody MemoCreateRequest req) {
        if (req.orderNo == null || req.content == null || req.content.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        CsMemo memo = CsMemo.builder()
            .orderNo(req.orderNo)
            .csType(req.csType)
            .csDept(req.csDept)
            .csKind(req.csKind)
            .content(req.content)
            .status(req.status != null ? req.status : "미처리")
            .writer(req.writer)
            .build();
        log.info("CS메모 저장: orderNo={}", req.orderNo);
        return ResponseEntity.ok(new MemoDTO(csMemoRepository.save(memo)));
    }

    // 메모 삭제
    @DeleteMapping("/{memoId}")
    @Transactional
    public ResponseEntity<Map<String,Object>> delete(@PathVariable String memoId) {
        csMemoRepository.deleteById(java.util.UUID.fromString(memoId));
        return ResponseEntity.ok(Map.of("success", true));
    }
}
