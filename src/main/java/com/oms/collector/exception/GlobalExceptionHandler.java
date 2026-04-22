package com.oms.collector.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LockConflictException.class)
    public ResponseEntity<Map<String, Object>> handleLockConflict(LockConflictException e) {
        log.warn("[WorkLock] 충돌: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
            "success",   false,
            "errorCode", "LOCK_CONFLICT",
            "message",   e.getMessage(),
            "lockedBy",  e.getLockedBy()
        ));
    }
}
