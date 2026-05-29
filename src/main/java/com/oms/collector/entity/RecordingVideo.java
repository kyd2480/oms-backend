package com.oms.collector.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "recording_videos")
@EntityListeners(AuditingEntityListener.class)
public class RecordingVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "recording_id")
    private UUID recordingId;

    @Column(name = "invoice_no", nullable = false, length = 100)
    private String invoiceNo;

    @Column(name = "order_no", length = 100)
    private String orderNo;

    @Column(name = "file_name", length = 300)
    private String fileName;

    @Column(name = "local_path", columnDefinition = "TEXT")
    private String localPath;

    @Column(name = "video_url", columnDefinition = "TEXT")
    private String videoUrl;

    @Column(name = "video_format", length = 30)
    private String videoFormat;

    @Column(name = "mode", length = 30)
    private String mode;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "duration_sec")
    private Double durationSec;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "pc_name", length = 150)
    private String pcName;

    @Column(name = "camera_setting", length = 300)
    private String cameraSetting;

    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
