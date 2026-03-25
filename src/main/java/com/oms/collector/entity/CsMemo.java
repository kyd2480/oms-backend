package com.oms.collector.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CS 메모 엔티티
 * 주문번호 기준으로 CS 처리 내역을 시간순으로 저장
 */
@Entity
@Table(name = "cs_memos")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CsMemo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "memo_id")
    private UUID memoId;

    @Column(name = "order_no", nullable = false, length = 100)
    private String orderNo;

    @Column(name = "cs_type", length = 50)
    private String csType;       // 함포CS, 고객CS 등

    @Column(name = "cs_dept", length = 50)
    private String csDept;       // 상담구분

    @Column(name = "cs_kind", length = 50)
    private String csKind;       // 종류

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;      // 처리내용

    @Column(name = "status", length = 20)
    private String status;       // 미처리, 처리중, 완료

    @Column(name = "writer", length = 50)
    private String writer;       // 작성자

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
