package com.oms.collector.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

public class PrintTypeDto {
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateRequest {
        private String code;
        private String name;
        private String description;
        private Integer sortOrder;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateRequest {
        private String name;
        private String description;
        private Integer sortOrder;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Response {
        private UUID printTypeId;
        private String code;
        private String name;
        private Boolean isActive;
        private Integer sortOrder;
        private String description;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
