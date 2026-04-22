package com.oms.collector.service;

import com.oms.collector.dto.SabangnetIntegrationDto;
import com.oms.collector.entity.SabangnetIntegration;
import com.oms.collector.repository.SabangnetIntegrationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SabangnetIntegrationService {

    private final SabangnetIntegrationRepository repository;

    @Transactional(readOnly = true)
    public List<SabangnetIntegrationDto> list() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public SabangnetIntegrationDto create(SabangnetIntegrationDto dto) {
        String companyCode = normalizeCompanyCode(dto.getCompanyCode());
        String sabangnetId = require(dto.getSabangnetId(), "사방넷 ID");
        String mallCode = blankToNull(dto.getMallCode());
        if (mallCode != null && repository.existsByCompanyCodeIgnoreCaseAndSabangnetIdIgnoreCaseAndMallCodeIgnoreCase(companyCode, sabangnetId, mallCode)) {
            throw new IllegalArgumentException("이미 등록된 사방넷 쇼핑몰 코드입니다");
        }

        SabangnetIntegration entity = SabangnetIntegration.builder()
            .companyCode(companyCode)
            .integrationName(require(dto.getIntegrationName(), "설정명"))
            .sabangnetId(sabangnetId)
            .mallCode(mallCode)
            .mallName(require(dto.getMallName(), "쇼핑몰명"))
            .apiKey(require(dto.getApiKey(), "API 인증키"))
            .apiBaseUrl(normalizeApiBaseUrl(dto.getApiBaseUrl()))
            .logisticsPlaceId(blankToNull(dto.getLogisticsPlaceId()))
            .enabled(dto.getEnabled() == null || dto.getEnabled())
            .memo(blankToNull(dto.getMemo()))
            .build();
        return toDto(repository.save(entity));
    }

    @Transactional
    public SabangnetIntegrationDto update(UUID id, SabangnetIntegrationDto dto) {
        SabangnetIntegration entity = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("사방넷 연동 설정을 찾을 수 없습니다"));

        entity.setCompanyCode(normalizeCompanyCode(dto.getCompanyCode()));
        entity.setIntegrationName(require(dto.getIntegrationName(), "설정명"));
        entity.setSabangnetId(require(dto.getSabangnetId(), "사방넷 ID"));
        entity.setMallCode(blankToNull(dto.getMallCode()));
        entity.setMallName(require(dto.getMallName(), "쇼핑몰명"));
        if (dto.getApiKey() != null && !dto.getApiKey().isBlank() && !dto.getApiKey().contains("*")) {
            entity.setApiKey(dto.getApiKey().trim());
        }
        entity.setApiBaseUrl(normalizeApiBaseUrl(dto.getApiBaseUrl()));
        entity.setLogisticsPlaceId(blankToNull(dto.getLogisticsPlaceId()));
        entity.setEnabled(dto.getEnabled() == null || dto.getEnabled());
        entity.setMemo(blankToNull(dto.getMemo()));
        return toDto(repository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("사방넷 연동 설정을 찾을 수 없습니다");
        }
        repository.deleteById(id);
    }

    @Transactional
    public SabangnetIntegrationDto toggle(UUID id) {
        SabangnetIntegration entity = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("사방넷 연동 설정을 찾을 수 없습니다"));
        entity.setEnabled(!Boolean.TRUE.equals(entity.getEnabled()));
        return toDto(repository.save(entity));
    }

    private SabangnetIntegrationDto toDto(SabangnetIntegration entity) {
        return SabangnetIntegrationDto.builder()
            .integrationId(entity.getIntegrationId())
            .companyCode(entity.getCompanyCode())
            .integrationName(entity.getIntegrationName())
            .sabangnetId(entity.getSabangnetId())
            .mallCode(entity.getMallCode())
            .mallName(entity.getMallName())
            .maskedApiKey(mask(entity.getApiKey()))
            .apiBaseUrl(entity.getApiBaseUrl())
            .logisticsPlaceId(entity.getLogisticsPlaceId())
            .enabled(entity.getEnabled())
            .memo(entity.getMemo())
            .lastCollectedAt(entity.getLastCollectedAt())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }

    private String normalizeCompanyCode(String value) {
        String code = value == null ? "C00" : value.trim().toUpperCase();
        if (code.isBlank()) {
            code = "C00";
        }
        if (!code.matches("[A-Z0-9_]{1,20}")) {
            throw new IllegalArgumentException("유효하지 않은 회사코드입니다");
        }
        return code;
    }

    private String normalizeApiBaseUrl(String value) {
        String url = require(value, "API URL");
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        return url;
    }

    private String require(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " 값을 입력하세요");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.length() <= 8) {
            return "********";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }
}
