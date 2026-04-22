package com.oms.collector.repository;

import com.oms.collector.entity.InvoiceApiLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InvoiceApiLogRepository extends JpaRepository<InvoiceApiLog, UUID> {

    List<InvoiceApiLog> findTop50ByOrderNoOrderByCreatedAtDesc(String orderNo);

    List<InvoiceApiLog> findTop50ByTrackingNoOrderByCreatedAtDesc(String trackingNo);
}
