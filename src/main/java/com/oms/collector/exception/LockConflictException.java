package com.oms.collector.exception;

public class LockConflictException extends RuntimeException {

    private final String lockedBy;

    public LockConflictException(String lockKey, String lockedBy) {
        super(lockedBy + "님이 이미 작업 중입니다");
        this.lockedBy = lockedBy;
    }

    public String getLockedBy() { return lockedBy; }
}
