package com.oms.collector.service;

import com.oms.collector.dto.WarehouseDto;
import com.oms.collector.entity.Warehouse;
import com.oms.collector.repository.WarehouseRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 창고 관리 Service
 *
 * - CRUD
 * - 초기 창고 데이터 자동 삽입 (최초 1회)
 * - InventoryService에서 창고명 조회 제공
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    // ── 초기 데이터: DB가 비어있을 때 기본 창고 35개 삽입 ──────────────────────
    @PostConstruct
    @Transactional
    public void initDefaultWarehouses() {
        if (warehouseRepository.count() > 0) return;

        log.info("🏭 기본 창고 데이터 초기화 시작...");

        Object[][] data = {
            // { code, name, type, sortOrder }
            // ── 실제창고 ──
            {"ANYANG",           "본사(안양)",                      "REAL",    1},
            {"BUCHEON",          "부천검수창고",                     "REAL",    2},
            {"ICHEON_BOX",       "고백창고(이천)_BOX",               "REAL",    3},
            {"ICHEON_PCS",       "고백창고(이천)_PCS",               "REAL",    4},
            {"ICHEON_EXP_ETC",   "고백창고(이천)-수출(기타해외)",     "REAL",    5},
            {"ICHEON_EXP_TWN",   "고백창고(이천)-수출(대만)",         "REAL",    6},
            {"ICHEON_EXP_CHN",   "고백창고(이천)-수출(중국)",         "REAL",    7},
            {"ICHEON_EXP_OFF",   "고백창고(이천)-수출(오프라인)",      "REAL",    8},
            {"SHOPEE",           "쇼피(싱가포르) 위탁판매",           "REAL",    9},
            {"CHAKAN",           "차칸",                             "REAL",   10},
            // ── 이동 중 ──
            {"ANYANG_TO_ICHEON", "2안양 → 고백창고(이천)",           "TRANSIT", 20},
            {"SESILCHECK",       "세실재고확인본*",                   "TRANSIT", 21},
            // ── 반품창고 ──
            {"ICHEON_RETURN",    "고백창고(이천)-오프라인반품",        "RETURN",  30},
            {"ONLINE_RETURN",    "고내온라인(반품)",                  "RETURN",  31},
            {"HQ_ONLINE_RET",    "본사(온라인반품)",                  "RETURN",  32},
            {"HQ_OFFLINE_RET",   "본사(오프라인반품)",                "RETURN",  33},
            // ── 불량/폐기 ──
            {"DEFECT",           "불량창고",                         "DEFECT",  40},
            {"ICHEON_DEFECT",    "고백창고(이천)-불량재고",            "DEFECT",  41},
            {"ICHEON_DISCARD",   "고백창고(이천)-폐기",               "DEFECT",  42},
            {"DEFECT_OUT",       "완불량반출대기",                    "DEFECT",  43},
            {"OJEON_SPECIAL",    "오전-원불량반출상대기",              "DEFECT",  44},
            // ── 특수창고 ──
            {"NEWREFUND",        "새로고침배상재고",                  "SPECIAL", 50},
            {"ICHEON_PCS_DIFF",  "이천PCS상이건",                    "SPECIAL", 51},
            {"DONATION",         "★기부★",                          "SPECIAL", 52},
            {"ICHEON_GIFT",      "고백창고(이천)-기부",               "SPECIAL", 53},
            {"ANYANG_SPECIAL",   "안양특판-재고확인보송",              "SPECIAL", 54},
            {"DP",               "본사(특별판매 DP)",                 "SPECIAL", 55},
            {"SIZE_35",          "3,5키재고(위탁판매)",               "SPECIAL", 56},
            // ── 가상재고 ──
            {"VIRTUAL_CS",       "(가상재고)CS고객택배배상건수",        "VIRTUAL", 60},
            {"VIRTUAL_HQ",       "(가상재고)본사블량",                 "VIRTUAL", 61},
            {"VIRTUAL_DEF",      "(가상재고)CS볼량",                  "VIRTUAL", 62},
            {"SEARCH_DEF",       "반전검색고(비가용)",                 "VIRTUAL", 63},
            // ── 미사용 ──
            {"UNUSED_ADJUST",    "(미사용)정정부",                    "UNUSED",  90},
            {"UNUSED_HQ",        "(미사용)본사",                      "UNUSED",  91},
            {"UNUSED_TO_ICHEON", "(미사용)본사 → 고백창고(이천)",      "UNUSED",  92},
        };

        for (Object[] row : data) {
            Warehouse wh = Warehouse.builder()
                .code((String) row[0])
                .name((String) row[1])
                .type((String) row[2])
                .sortOrder((Integer) row[3])
                .isActive(!((String) row[2]).equals("UNUSED"))
                .build();
            warehouseRepository.save(wh);
        }

        log.info("✅ 기본 창고 {}개 초기화 완료", data.length);
    }

    // ── 조회 ───────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<WarehouseDto.Response> getAll() {
        return warehouseRepository.findAllByOrderBySortOrderAscNameAsc()
            .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<WarehouseDto.Response> getActive() {
        return warehouseRepository.findByIsActiveTrueOrderBySortOrderAscNameAsc()
            .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<WarehouseDto.Response> search(String keyword) {
        return warehouseRepository.searchByKeyword(keyword)
            .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public WarehouseDto.Response getById(UUID id) {
        return warehouseRepository.findById(id)
            .map(this::toDto)
            .orElseThrow(() -> new RuntimeException("창고를 찾을 수 없습니다: " + id));
    }

    // ── 생성 ───────────────────────────────────────────────────────────────────

    @Transactional
    public WarehouseDto.Response create(WarehouseDto.CreateRequest req) {
        String code = req.getCode().trim().toUpperCase();

        if (warehouseRepository.existsByCode(code)) {
            throw new RuntimeException("이미 존재하는 창고 코드입니다: " + code);
        }

        Warehouse wh = Warehouse.builder()
            .code(code)
            .name(req.getName().trim())
            .type(req.getType() != null ? req.getType() : "REAL")
            .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : 999)
            .description(req.getDescription())
            .isActive(true)
            .build();

        log.info("🏭 창고 생성: {} ({})", wh.getName(), wh.getCode());
        return toDto(warehouseRepository.save(wh));
    }

    // ── 수정 ───────────────────────────────────────────────────────────────────

    @Transactional
    public WarehouseDto.Response update(UUID id, WarehouseDto.UpdateRequest req) {
        Warehouse wh = warehouseRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("창고를 찾을 수 없습니다: " + id));

        if (req.getName() != null)       wh.setName(req.getName().trim());
        if (req.getType() != null)       wh.setType(req.getType());
        if (req.getDescription() != null) wh.setDescription(req.getDescription());
        if (req.getSortOrder() != null)  wh.setSortOrder(req.getSortOrder());

        log.info("✏️ 창고 수정: {} ({})", wh.getName(), wh.getCode());
        return toDto(warehouseRepository.save(wh));
    }

    // ── 활성/비활성 토글 ────────────────────────────────────────────────────────

    @Transactional
    public WarehouseDto.Response toggleActive(UUID id) {
        Warehouse wh = warehouseRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("창고를 찾을 수 없습니다: " + id));

        wh.setIsActive(!wh.getIsActive());
        log.info("🔄 창고 상태 변경: {} → {}", wh.getName(), wh.getIsActive() ? "활성" : "비활성");
        return toDto(warehouseRepository.save(wh));
    }

    // ── 삭제 ───────────────────────────────────────────────────────────────────

    @Transactional
    public void delete(UUID id) {
        Warehouse wh = warehouseRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("창고를 찾을 수 없습니다: " + id));
        log.info("🗑️ 창고 삭제: {} ({})", wh.getName(), wh.getCode());
        warehouseRepository.delete(wh);
    }

    // ── InventoryService에서 창고명 유효성 검증용 ─────────────────────────────

    @Transactional(readOnly = true)
    public Warehouse findByCodeOrThrow(String code) {
        return warehouseRepository.findByCode(code)
            .orElseThrow(() -> new RuntimeException("존재하지 않는 창고 코드입니다: " + code));
    }

    @Transactional(readOnly = true)
    public boolean existsByCode(String code) {
        return warehouseRepository.existsByCode(code);
    }

    // ── DTO 변환 ───────────────────────────────────────────────────────────────

    private WarehouseDto.Response toDto(Warehouse wh) {
        return WarehouseDto.Response.builder()
            .warehouseId(wh.getWarehouseId())
            .code(wh.getCode())
            .name(wh.getName())
            .type(wh.getType())
            .isActive(wh.getIsActive())
            .sortOrder(wh.getSortOrder())
            .description(wh.getDescription())
            .createdAt(wh.getCreatedAt())
            .updatedAt(wh.getUpdatedAt())
            .build();
    }
}
