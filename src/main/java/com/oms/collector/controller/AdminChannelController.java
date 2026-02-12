package com.oms.collector.controller;

import com.oms.collector.dto.SalesChannelDto;
import com.oms.collector.entity.SalesChannel;
import com.oms.collector.repository.SalesChannelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 판매처 관리 API
 * 
 * 판매처 CRUD 및 관리 기능 제공
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/channels")
@RequiredArgsConstructor
public class AdminChannelController {
    
    private final SalesChannelRepository salesChannelRepository;
    
    /**
     * 판매처 목록 조회
     */
    @GetMapping
    public ResponseEntity<List<SalesChannelDto>> getChannels() {
        List<SalesChannel> channels = salesChannelRepository.findAll();
        
        List<SalesChannelDto> dtos = channels.stream()
            .map(this::toDto)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }
    
    /**
     * 판매처 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<SalesChannelDto> getChannel(@PathVariable UUID id) {
        return salesChannelRepository.findById(id)
            .map(this::toDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * 판매처 추가
     */
    @PostMapping
    public ResponseEntity<SalesChannelDto> createChannel(@RequestBody SalesChannelDto dto) {
        log.info("🆕 판매처 추가: {}", dto.getChannelName());
        
        // 중복 체크
        if (salesChannelRepository.existsByChannelCode(dto.getChannelCode())) {
            return ResponseEntity.badRequest().build();
        }
        
        LocalDateTime now = LocalDateTime.now();
        SalesChannel channel = SalesChannel.builder()
            .channelCode(dto.getChannelCode())
            .channelName(dto.getChannelName())
            .apiType(dto.getApiType())
            .apiBaseUrl(dto.getApiBaseUrl())
            .credentials(dto.getCredentials())
            .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
            .collectionInterval(dto.getCollectionInterval() != null ? dto.getCollectionInterval() : 10)
            .createdAt(now)
            .updatedAt(now)
            .build();
        
        SalesChannel saved = salesChannelRepository.save(channel);
        
        return ResponseEntity.ok(toDto(saved));
    }
    
    /**
     * 판매처 수정
     */
    @PutMapping("/{id}")
    public ResponseEntity<SalesChannelDto> updateChannel(
            @PathVariable UUID id,
            @RequestBody SalesChannelDto dto) {
        
        log.info("✏️ 판매처 수정: {} ({})", dto.getChannelName(), id);
        
        return salesChannelRepository.findById(id)
            .map(channel -> {
                channel.setChannelName(dto.getChannelName());
                channel.setApiType(dto.getApiType());
                channel.setApiBaseUrl(dto.getApiBaseUrl());
                channel.setCredentials(dto.getCredentials());
                channel.setCollectionInterval(dto.getCollectionInterval());
                channel.setUpdatedAt(LocalDateTime.now());
                
                SalesChannel updated = salesChannelRepository.save(channel);
                return ResponseEntity.ok(toDto(updated));
            })
            .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * 판매처 삭제
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteChannel(@PathVariable UUID id) {
        log.info("🗑️ 판매처 삭제: {}", id);
        
        if (!salesChannelRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        
        salesChannelRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * 판매처 활성화/비활성화 토글
     */
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<SalesChannelDto> toggleChannel(@PathVariable UUID id) {
        return salesChannelRepository.findById(id)
            .map(channel -> {
                channel.setIsActive(!channel.getIsActive());
                channel.setUpdatedAt(LocalDateTime.now());
                
                SalesChannel updated = salesChannelRepository.save(channel);
                
                log.info("🔄 판매처 {} 상태 변경: {}", 
                    channel.getChannelName(), 
                    channel.getIsActive() ? "활성화" : "비활성화");
                
                return ResponseEntity.ok(toDto(updated));
            })
            .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Entity → DTO 변환
     */
    private SalesChannelDto toDto(SalesChannel channel) {
        return SalesChannelDto.builder()
            .channelId(channel.getChannelId())
            .channelCode(channel.getChannelCode())
            .channelName(channel.getChannelName())
            .apiType(channel.getApiType())
            .apiBaseUrl(channel.getApiBaseUrl())
            .credentials(channel.getCredentials())
            .isActive(channel.getIsActive())
            .collectionInterval(channel.getCollectionInterval())
            .lastCollectedAt(channel.getLastCollectedAt())
            .createdAt(channel.getCreatedAt())
            .updatedAt(channel.getUpdatedAt())
            .build();
    }
}
