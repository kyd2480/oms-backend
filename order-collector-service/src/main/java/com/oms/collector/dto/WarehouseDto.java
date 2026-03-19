package com.oms.collector.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 창고 관련 DTO
 */
public class WarehouseDto {

    /** 창고 생성 요청 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {
        private String code;       // 영문 코드 (예: ICHEON_BOX)
        private String name;       // 창고명 (예: 고백창고(이천)_BOX)
        private String type;       // REAL | TRANSIT | RETURN | DEFECT | SPECIAL | VIRTUAL | UNUSED
        private String description;
        private Integer sortOrder;
    }

    /** 창고 수정 요청 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateRequest {
        private String name;
        private String type;
        private String description;
        private Integer sortOrder;
    }

    /** 창고 응답 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private UUID warehouseId;
        private String code;
        private String name;
        private String type;
        private Boolean isActive;
        private Integer sortOrder;
        private String description;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
