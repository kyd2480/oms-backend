package com.oms.collector.controller;

import com.oms.collector.entity.RecordingVideo;
import com.oms.collector.repository.RecordingVideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/recording-videos")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RecordingVideoController {

    private final RecordingVideoRepository recordingVideoRepository;

    @Value("${recording.video.storage-dir:}")
    private String configuredStorageDir;

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

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<?> upload(
        @RequestParam("file") MultipartFile file,
        @RequestParam String invoiceNo,
        @RequestParam(required = false) String orderNo,
        @RequestParam(required = false) String videoFormat,
        @RequestParam(required = false) String mode,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Double durationSec,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startedAt,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endedAt,
        @RequestParam(required = false) String pcName,
        @RequestParam(required = false) String cameraSetting,
        @RequestParam(required = false) String memo
    ) throws IOException {
        String normalizedInvoice = normalize(invoiceNo);
        if (normalizedInvoice == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "invoiceNo는 필수입니다."));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "업로드 파일이 없습니다."));
        }

        RecordingVideo video = RecordingVideo.builder()
            .invoiceNo(normalizedInvoice)
            .orderNo(normalize(orderNo))
            .fileName(safeFileName(file.getOriginalFilename()))
            .videoFormat(normalize(videoFormat))
            .mode(normalize(mode))
            .status(normalize(status) != null ? normalize(status) : "SAVED")
            .durationSec(durationSec)
            .startedAt(startedAt)
            .endedAt(endedAt)
            .pcName(normalize(pcName))
            .cameraSetting(normalize(cameraSetting))
            .memo(normalize(memo))
            .build();

        RecordingVideo saved = recordingVideoRepository.save(video);
        String extension = extensionFromFileName(video.getFileName());
        Path targetDir = storageRoot().resolve(safeFileName(normalizedInvoice));
        Files.createDirectories(targetDir);
        Path target = targetDir.resolve(saved.getRecordingId() + extension);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        saved.setLocalPath(target.toString());
        saved.setVideoUrl(streamUrl(saved.getRecordingId()));
        saved = recordingVideoRepository.save(saved);
        return ResponseEntity.ok(Map.of("success", true, "video", RecordingVideoDto.from(saved)));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<RecordingVideoDto>> find(
        @RequestParam(required = false) String invoiceNo,
        @RequestParam(required = false) String orderNo,
        @RequestParam(required = false, defaultValue = "true") boolean uploadedOnly
    ) {
        String normalizedInvoice = normalize(invoiceNo);
        String normalizedOrderNo = normalize(orderNo);
        List<RecordingVideo> videos;
        if (normalizedInvoice != null) {
            videos = uploadedOnly
                ? recordingVideoRepository.findTop50ByInvoiceNoAndVideoUrlIsNotNullOrderByCreatedAtDesc(normalizedInvoice)
                : recordingVideoRepository.findTop50ByInvoiceNoOrderByCreatedAtDesc(normalizedInvoice);
        } else if (normalizedOrderNo != null) {
            videos = uploadedOnly
                ? recordingVideoRepository.findTop50ByOrderNoAndVideoUrlIsNotNullOrderByCreatedAtDesc(normalizedOrderNo)
                : recordingVideoRepository.findTop50ByOrderNoOrderByCreatedAtDesc(normalizedOrderNo);
        } else {
            videos = List.of();
        }
        return ResponseEntity.ok(videos.stream().map(RecordingVideoDto::from).toList());
    }

    @GetMapping("/{recordingId}/stream")
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> stream(@PathVariable UUID recordingId) {
        RecordingVideo video = recordingVideoRepository.findById(recordingId)
            .orElseThrow(() -> new IllegalArgumentException("녹화 영상을 찾을 수 없습니다: " + recordingId));
        String pathText = normalize(video.getLocalPath());
        if (pathText == null) {
            return ResponseEntity.notFound().build();
        }
        Path path = Path.of(pathText);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(path);
        MediaType contentType = mediaTypeFor(video.getFileName(), path);
        return ResponseEntity.ok()
            .contentType(contentType)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + safeFileName(video.getFileName()) + "\"")
            .body(resource);
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Path storageRoot() throws IOException {
        String configured = normalize(configuredStorageDir);
        Path root = configured != null
            ? Path.of(configured)
            : Path.of(System.getProperty("java.io.tmpdir"), "recording-videos");
        Files.createDirectories(root);
        return root;
    }

    private String streamUrl(UUID recordingId) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/recording-videos/")
            .path(recordingId.toString())
            .path("/stream")
            .toUriString();
    }

    private String safeFileName(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return "recording";
        }
        String cleaned = normalized.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        return cleaned.isBlank() ? "recording" : cleaned;
    }

    private String extensionFromFileName(String fileName) {
        String cleaned = safeFileName(fileName);
        int dot = cleaned.lastIndexOf('.');
        if (dot < 0 || dot == cleaned.length() - 1) {
            return ".mp4";
        }
        String extension = cleaned.substring(dot).toLowerCase();
        return extension.matches("\\.(mp4|avi|mov|mkv|webm)") ? extension : ".mp4";
    }

    private MediaType mediaTypeFor(String fileName, Path path) {
        String lower = safeFileName(fileName != null ? fileName : path.getFileName().toString()).toLowerCase();
        if (lower.endsWith(".avi")) {
            return MediaType.parseMediaType("video/x-msvideo");
        }
        if (lower.endsWith(".webm")) {
            return MediaType.parseMediaType("video/webm");
        }
        return MediaType.parseMediaType("video/mp4");
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
