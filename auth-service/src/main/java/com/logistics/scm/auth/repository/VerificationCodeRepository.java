package com.logistics.scm.auth.repository;

import com.logistics.scm.auth.entity.VerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, UUID> {

    List<VerificationCode> findByPurposeAndMethodAndTargetValueAndUsedFalse(
        VerificationCode.Purpose purpose,
        VerificationCode.Method method,
        String targetValue
    );

    List<VerificationCode> findByPurposeAndMethodAndTargetValueAndUsernameAndUsedFalse(
        VerificationCode.Purpose purpose,
        VerificationCode.Method method,
        String targetValue,
        String username
    );

    Optional<VerificationCode> findTopByPurposeAndMethodAndTargetValueAndUsernameAndUsedFalseOrderByCreatedAtDesc(
        VerificationCode.Purpose purpose,
        VerificationCode.Method method,
        String targetValue,
        String username
    );

    Optional<VerificationCode> findTopByPurposeAndMethodAndTargetValueAndUsedFalseOrderByCreatedAtDesc(
        VerificationCode.Purpose purpose,
        VerificationCode.Method method,
        String targetValue
    );

    Optional<VerificationCode> findTopByPurposeAndMethodAndTargetValueAndVerificationTokenAndVerifiedTrueAndUsedFalseOrderByCreatedAtDesc(
        VerificationCode.Purpose purpose,
        VerificationCode.Method method,
        String targetValue,
        String verificationToken
    );
}
