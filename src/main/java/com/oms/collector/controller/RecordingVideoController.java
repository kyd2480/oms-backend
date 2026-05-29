package com.oms.collector.controller;

import com.oms.collector.entity.RecordingVideo;
import com.oms.collector.repository.RecordingVideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recording-videos")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RecordingVideoController {

    private final RecordingVideoRepository recordingVideoRepository;

    @PostMapping
    @Transactional
    public ResponseEntity<?> save(@RequestBody RecordingVideoRequest request) {
        String invoiceNo = normalize(request.invoiceNo);
        if (invoiceNo == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "invoiceNo는 필수입니다."
            ));
        }

        RecordingVideo video = RecordingVideo.builder()
            .invoiceNo(invoiceNo)
            .orderNo(normalize(request.orderNo))
            .fileName(normalize(request.fileName))
            .localPath(normalize(request.localPath))
            .videoUrl(normalize(request.videoUrl))
            .videoFormat(normalize(request.videoFormat))
            .mode(normalize(request.mode))
            .status(normalize(request.status) != null ? normalize(request.status) : "SAVED")
            .durationSec(request.durationSec)
            .startedAt(request.startedAt)
            .endedAt(request.endedAt)
            .pcName(normalize(request.pcName))
            .cameraSetting(normalize(request.cameraSetting))
            .memo(normalize(request.memo))
            .build();

        RecordingVideo saved = recordingVideoRepository.save(video);
        return ResponseEntity.ok(Map.of("success", true, "video", RecordingVideoDto.from(saved)));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<RecordingVideoDto>> find(
        @RequestParam(required = false) String invoiceNo,
        @RequestParam(required = false) String orderNo
    ) {
        String normalizedInvoice = normalize(invoiceNo);
        String normalizedOrderNo = normalize(orderNo);
        List<RecordingVideo> videos;
        if (normalizedInvoice != null) {
            videos = recordingVideoRepository.findTop50ByInvoiceNoOrderByCreatedAtDesc(normalizedInvoice);
        } else if (normalizedOrderNo != null) {
            videos = recordingVideoRepository.findTop50ByOrderNoOrderByCreatedAtDesc(normalizedOrderNo);
        } else {
            videos = List.of();
        }
        return ResponseEntity.ok(videos.stream().map(RecordingVideoDto::from).toList());
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static class RecordingVideoRequest {
        public String invoiceNo;
        public String orderNo;
        public String fileName;
        public String localPath;
        public String videoUrl;
        public String videoFormat;
        public String mode;
        public String status;
        public Double durationSec;
        public LocalDateTime startedAt;
        public LocalDateTime endedAt;
        public String pcName;
        public String cameraSetting;
        public String memo;
    }

    public record RecordingVideoDto(
        String recordingId,
        String invoiceNo,
        String orderNo,
        String fileName,
        String localPath,
        String videoUrl,
        String videoFormat,
        String mode,
        String status,
        Double durationSec,
        String startedAt,
        String endedAt,
        String pcName,
        String cameraSetting,
        String memo,
        String createdAt
    ) {
        static RecordingVideoDto from(RecordingVideo video) {
            return new RecordingVideoDto(
                video.getRecordingId() != null ? video.getRecordingId().toString() : null,
                video.getInvoiceNo(),
                video.getOrderNo(),
                video.getFileName(),
                video.getLocalPath(),
                video.getVideoUrl(),
                video.getVideoFormat(),
                video.getMode(),
                video.getStatus(),
                video.getDurationSec(),
                video.getStartedAt() != null ? video.getStartedAt().toString() : null,
                video.getEndedAt() != null ? video.getEndedAt().toString() : null,
                video.getPcName(),
                video.getCameraSetting(),
                video.getMemo(),
                video.getCreatedAt() != null ? video.getCreatedAt().toString() : null
            );
        }
    }
}
