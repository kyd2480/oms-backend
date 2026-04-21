package com.oms.collector.service;

import com.oms.collector.dto.PrintTypeDto;
import com.oms.collector.entity.PrintType;
import com.oms.collector.repository.PrintTypeRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@DependsOn("operationalSchemaMigration")
@RequiredArgsConstructor
public class PrintTypeService {
    private final PrintTypeRepository printTypeRepository;

    @PostConstruct
    @Transactional
    public void initDefaultPrintTypes() {
        if (printTypeRepository.count() > 0) return;
        Object[][] data = {
            {"NORMAL", "일반건", 1},
            {"SEPARATE", "별도인쇄", 2},
            {"DUTY_FREE", "면세점", 3},
        };
        for (Object[] row : data) {
            printTypeRepository.save(PrintType.builder()
                .code((String) row[0])
                .name((String) row[1])
                .sortOrder((Integer) row[2])
                .isActive(true)
                .build());
        }
        log.info("인쇄구분 기본값 초기화 완료");
    }

    @Transactional(readOnly = true)
    public List<PrintTypeDto.Response> getAll() {
        return printTypeRepository.findAllByOrderBySortOrderAscNameAsc().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PrintTypeDto.Response> getActive() {
        return printTypeRepository.findByIsActiveTrueOrderBySortOrderAscNameAsc().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PrintTypeDto.Response> search(String keyword) {
        return printTypeRepository.searchByKeyword(keyword).stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public PrintTypeDto.Response create(PrintTypeDto.CreateRequest req) {
        String code = normalizeCode(req.getCode());
        if (printTypeRepository.existsByCode(code)) {
            throw new RuntimeException("이미 존재하는 인쇄구분 코드입니다: " + code);
        }
        PrintType printType = PrintType.builder()
            .code(code)
            .name(required(req.getName(), "인쇄구분명"))
            .description(req.getDescription())
            .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : 999)
            .isActive(true)
            .build();
        return toDto(printTypeRepository.save(printType));
    }

    @Transactional
    public PrintTypeDto.Response update(UUID id, PrintTypeDto.UpdateRequest req) {
        PrintType printType = printTypeRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("인쇄구분을 찾을 수 없습니다: " + id));
        if (req.getName() != null) printType.setName(required(req.getName(), "인쇄구분명"));
        if (req.getDescription() != null) printType.setDescription(req.getDescription());
        if (req.getSortOrder() != null) printType.setSortOrder(req.getSortOrder());
        return toDto(printTypeRepository.save(printType));
    }

    @Transactional
    public PrintTypeDto.Response toggleActive(UUID id) {
        PrintType printType = printTypeRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("인쇄구분을 찾을 수 없습니다: " + id));
        printType.setIsActive(!Boolean.TRUE.equals(printType.getIsActive()));
        return toDto(printTypeRepository.save(printType));
    }

    @Transactional
    public void delete(UUID id) {
        printTypeRepository.delete(printTypeRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("인쇄구분을 찾을 수 없습니다: " + id)));
    }

    private String normalizeCode(String code) {
        String value = required(code, "인쇄구분 코드").trim().toUpperCase().replaceAll("[^A-Z0-9_]", "_");
        if (value.isBlank()) throw new RuntimeException("인쇄구분 코드 필요");
        return value;
    }

    private String required(String value, String name) {
        if (value == null || value.trim().isBlank()) throw new RuntimeException(name + " 필요");
        return value.trim();
    }

    private PrintTypeDto.Response toDto(PrintType printType) {
        return PrintTypeDto.Response.builder()
            .printTypeId(printType.getPrintTypeId())
            .code(printType.getCode())
            .name(printType.getName())
            .isActive(printType.getIsActive())
            .sortOrder(printType.getSortOrder())
            .description(printType.getDescription())
            .createdAt(printType.getCreatedAt())
            .updatedAt(printType.getUpdatedAt())
            .build();
    }
}
