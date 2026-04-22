package com.oms.collector.service;

import com.oms.collector.dto.CarrierContractDto;
import com.oms.collector.entity.CarrierContract;
import com.oms.collector.repository.CarrierContractRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CarrierContractService {

    private final CarrierContractRepository repository;

    @Transactional(readOnly = true)
    public List<CarrierContractDto> list() {
        return repository.findAllByOrderByCarrierCodeAscCreatedAtDesc().stream().map(this::toDto).toList();
    }

    @Transactional
    public CarrierContractDto create(CarrierContractDto dto) {
        CarrierContract entity = CarrierContract.builder().build();
        apply(entity, dto, true);
        CarrierContract saved = repository.save(entity);
        if (Boolean.TRUE.equals(saved.getIsDefault())) clearOtherDefaults(saved);
        return toDto(saved);
    }

    @Transactional
    public CarrierContractDto update(UUID id, CarrierContractDto dto) {
        CarrierContract entity = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("택배 계약 설정을 찾을 수 없습니다"));
        apply(entity, dto, false);
        CarrierContract saved = repository.save(entity);
        if (Boolean.TRUE.equals(saved.getIsDefault())) clearOtherDefaults(saved);
        return toDto(saved);
    }

    @Transactional
    public CarrierContractDto toggle(UUID id) {
        CarrierContract entity = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("택배 계약 설정을 찾을 수 없습니다"));
        entity.setEnabled(!Boolean.TRUE.equals(entity.getEnabled()));
        return toDto(repository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("택배 계약 설정을 찾을 수 없습니다");
        }
        repository.deleteById(id);
    }

    private void apply(CarrierContract entity, CarrierContractDto dto, boolean create) {
        entity.setCompanyCode(normalizeCompanyCode(dto.getCompanyCode()));
        entity.setCarrierCode(require(dto.getCarrierCode(), "택배사 코드").toUpperCase());
        entity.setCarrierName(require(dto.getCarrierName(), "택배사명"));
        entity.setContractName(require(dto.getContractName(), "계약명"));
        entity.setIsDefault(Boolean.TRUE.equals(dto.getIsDefault()));
        entity.setEnabled(dto.getEnabled() == null || dto.getEnabled());
        entity.setApiBaseUrl(blankToNull(dto.getApiBaseUrl()));
        setSecret(entity::setAuthKey, entity.getAuthKey(), dto.getAuthKey(), create, false);
        setSecret(entity::setSeedKey, entity.getSeedKey(), dto.getSeedKey(), create, false);
        entity.setCustomerNo(blankToNull(dto.getCustomerNo()));
        entity.setContractApprovalNo(blankToNull(dto.getContractApprovalNo()));
        entity.setOfficeSer(blankToNull(dto.getOfficeSer()));
        entity.setContentCode(blankToNull(dto.getContentCode()));
        entity.setSenderCompanyName(blankToNull(dto.getSenderCompanyName()));
        entity.setSenderTel(blankToNull(dto.getSenderTel()));
        entity.setSenderZip(blankToNull(dto.getSenderZip()));
        entity.setSenderAddress(blankToNull(dto.getSenderAddress()));
        entity.setSenderAddressDetail(blankToNull(dto.getSenderAddressDetail()));
        entity.setTestYn(normalizeYn(dto.getTestYn()));
        entity.setPrintYn(normalizeYn(dto.getPrintYn()));
        entity.setMemo(blankToNull(dto.getMemo()));
    }

    private void clearOtherDefaults(CarrierContract selected) {
        repository.findByCompanyCodeIgnoreCaseAndCarrierCodeIgnoreCase(selected.getCompanyCode(), selected.getCarrierCode())
            .stream()
            .filter(item -> !item.getContractId().equals(selected.getContractId()))
            .filter(item -> Boolean.TRUE.equals(item.getIsDefault()))
            .forEach(item -> {
                item.setIsDefault(false);
                repository.save(item);
            });
    }

    private CarrierContractDto toDto(CarrierContract entity) {
        return CarrierContractDto.builder()
            .contractId(entity.getContractId())
            .companyCode(entity.getCompanyCode())
            .carrierCode(entity.getCarrierCode())
            .carrierName(entity.getCarrierName())
            .contractName(entity.getContractName())
            .isDefault(entity.getIsDefault())
            .enabled(entity.getEnabled())
            .apiBaseUrl(entity.getApiBaseUrl())
            .maskedAuthKey(mask(entity.getAuthKey()))
            .maskedSeedKey(mask(entity.getSeedKey()))
            .customerNo(entity.getCustomerNo())
            .contractApprovalNo(entity.getContractApprovalNo())
            .officeSer(entity.getOfficeSer())
            .contentCode(entity.getContentCode())
            .senderCompanyName(entity.getSenderCompanyName())
            .senderTel(entity.getSenderTel())
            .senderZip(entity.getSenderZip())
            .senderAddress(entity.getSenderAddress())
            .senderAddressDetail(entity.getSenderAddressDetail())
            .testYn(entity.getTestYn())
            .printYn(entity.getPrintYn())
            .memo(entity.getMemo())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }

    private void setSecret(java.util.function.Consumer<String> setter, String current, String next, boolean create, boolean required) {
        if (next != null && !next.isBlank() && !next.contains("*")) {
            setter.accept(next.trim());
        } else if (create && required) {
            throw new IllegalArgumentException("인증 정보를 입력하세요");
        } else if (create) {
            setter.accept(blankToNull(next));
        } else {
            setter.accept(current);
        }
    }

    private String normalizeCompanyCode(String value) {
        String code = value == null ? "C00" : value.trim().toUpperCase();
        if (code.isBlank()) code = "C00";
        if (!code.matches("[A-Z0-9_]{1,20}")) throw new IllegalArgumentException("유효하지 않은 회사코드입니다");
        return code;
    }

    private String normalizeYn(String value) {
        if (value == null || value.isBlank()) return "N";
        return "Y".equalsIgnoreCase(value.trim()) ? "Y" : "N";
    }

    private String require(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " 값을 입력하세요");
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) return "";
        if (value.length() <= 8) return "********";
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }
}
