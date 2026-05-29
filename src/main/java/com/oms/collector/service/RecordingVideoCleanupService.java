package com.oms.collector.service;

import com.oms.collector.entity.RecordingVideo;
import com.oms.collector.repository.RecordingVideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingVideoCleanupService {

    private final RecordingVideoRepository recordingVideoRepository;

    @Value("${recording.video.cleanup-enabled:true}")
    private boolean cleanupEnabled;

    @Value("${recording.video.retention-months:2}")
    private long retentionMonths;

    @Value("${recording.video.storage-dir:}")
    private String configuredStorageDir;

    @Scheduled(cron = "${recording.video.cleanup-cron:0 30 3 * * *}", zone = "Asia/Seoul")
    @Transactional
    public void cleanupExpiredVideos() {
        if (!cleanupEnabled) {
            return;
        }

        long months = Math.max(1, retentionMonths);
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(months);

        Path storageRoot = storageRoot();
        int totalRecords = 0;
        int totalDeletedFiles = 0;
        int totalSkippedFiles = 0;
        while (true) {
            List<RecordingVideo> expiredVideos =
                recordingVideoRepository.findTop1000ByCreatedAtBeforeOrderByCreatedAtAsc(cutoff);
            if (expiredVideos.isEmpty()) {
                break;
            }

            int deletedFiles = 0;
            int skippedFiles = 0;
            for (RecordingVideo video : expiredVideos) {
                FileDeleteResult result = deleteVideoFile(video, storageRoot);
                if (result == FileDeleteResult.DELETED || result == FileDeleteResult.NOT_FOUND) {
                    deletedFiles++;
                } else {
                    skippedFiles++;
                }
            }

            recordingVideoRepository.deleteAllInBatch(expiredVideos);
            totalRecords += expiredVideos.size();
            totalDeletedFiles += deletedFiles;
            totalSkippedFiles += skippedFiles;
        }

        if (totalRecords > 0) {
            log.info(
                "Expired recording videos cleaned: records={}, filesDeletedOrMissing={}, filesSkipped={}, cutoff={}",
                totalRecords,
                totalDeletedFiles,
                totalSkippedFiles,
                cutoff
            );
        }
    }

    private FileDeleteResult deleteVideoFile(RecordingVideo video, Path storageRoot) {
        String localPath = normalize(video.getLocalPath());
        if (localPath == null) {
            return FileDeleteResult.NOT_FOUND;
        }

        Path target = Path.of(localPath).toAbsolutePath().normalize();
        if (storageRoot != null && !target.startsWith(storageRoot)) {
            log.warn("Skip recording file outside storage root: recordingId={}, path={}", video.getRecordingId(), target);
            return FileDeleteResult.SKIPPED;
        }

        try {
            if (!Files.exists(target)) {
                return FileDeleteResult.NOT_FOUND;
            }
            if (!Files.isRegularFile(target)) {
                log.warn("Skip non-file recording path: recordingId={}, path={}", video.getRecordingId(), target);
                return FileDeleteResult.SKIPPED;
            }

            Files.delete(target);
            deleteEmptyParentDirectories(target, storageRoot);
            return FileDeleteResult.DELETED;
        } catch (IOException e) {
            log.warn("Failed to delete expired recording file: recordingId={}, path={}, message={}",
                video.getRecordingId(), target, e.getMessage());
            return FileDeleteResult.SKIPPED;
        }
    }

    private void deleteEmptyParentDirectories(Path filePath, Path storageRoot) {
        if (storageRoot == null) {
            return;
        }

        Path parent = filePath.getParent();
        while (parent != null && !parent.equals(storageRoot) && parent.startsWith(storageRoot)) {
            try (var entries = Files.list(parent)) {
                if (entries.findAny().isPresent()) {
                    return;
                }
                Files.deleteIfExists(parent);
                parent = parent.getParent();
            } catch (IOException ignored) {
                // Empty date directories are only a cleanup nicety.
                return;
            }
        }
    }

    private Path storageRoot() {
        String configured = normalize(configuredStorageDir);
        if (configured == null) {
            return null;
        }
        return Path.of(configured).toAbsolutePath().normalize();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private enum FileDeleteResult {
        DELETED,
        NOT_FOUND,
        SKIPPED
    }
}
