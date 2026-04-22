package com.oms.collector.service;

import com.oms.collector.entity.InvoiceApiLog;
import com.oms.collector.repository.InvoiceApiLogRepository;
import com.oms.collector.service.tracking.TrackingNumberProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class InvoiceApiLogService {

    private final InvoiceApiLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logIssueSuccess(String orderNo, String carrierCode, String carrierName,
                                TrackingNumberProvider.IssueResult result) {
        repository.save(InvoiceApiLog.builder()
            .orderNo(orderNo)
            .trackingNo(result.trackingNo())
            .carrierCode(carrierCode)
            .carrierName(carrierName)
            .actionType(InvoiceApiLog.ActionType.ISSUE)
            .apiProvider(valueOrDefault(result.apiProvider(), carrierCode))
            .success(true)
            .responseCode(valueOrDefault(result.responseCode(), "SUCCESS"))
            .responseMessage(valueOrDefault(result.responseMessage(), "송장번호 발급 성공"))
            .rawResponse(result.rawResponse())
            .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logCancelSuccess(String orderNo, String carrierCode, String carrierName,
                                 String trackingNo, TrackingNumberProvider.CancelResult result) {
        repository.save(InvoiceApiLog.builder()
            .orderNo(orderNo)
            .trackingNo(trackingNo)
            .carrierCode(carrierCode)
            .carrierName(carrierName)
            .actionType(InvoiceApiLog.ActionType.CANCEL)
            .apiProvider(valueOrDefault(result.apiProvider(), carrierCode))
            .success(true)
            .responseCode(valueOrDefault(result.responseCode(), "SUCCESS"))
            .responseMessage(valueOrDefault(result.responseMessage(), "송장취소 성공"))
            .rawResponse(result.rawResponse())
            .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(String orderNo, String trackingNo, String carrierCode, String carrierName,
                           InvoiceApiLog.ActionType actionType, Exception e) {
        repository.save(InvoiceApiLog.builder()
            .orderNo(orderNo)
            .trackingNo(trackingNo)
            .carrierCode(carrierCode)
            .carrierName(carrierName)
            .actionType(actionType)
            .apiProvider(carrierCode)
            .success(false)
            .responseCode(e.getClass().getSimpleName())
            .responseMessage(e.getMessage())
            .rawResponse(stackSafeMessage(e))
            .build());
    }

    @Transactional(readOnly = true)
    public List<InvoiceApiLog> findByOrderNo(String orderNo) {
        return repository.findTop50ByOrderNoOrderByCreatedAtDesc(orderNo);
    }

    @Transactional(readOnly = true)
    public List<InvoiceApiLog> findByTrackingNo(String trackingNo) {
        return repository.findTop50ByTrackingNoOrderByCreatedAtDesc(trackingNo);
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String stackSafeMessage(Exception e) {
        return Objects.toString(e.getMessage(), e.toString());
    }
}
