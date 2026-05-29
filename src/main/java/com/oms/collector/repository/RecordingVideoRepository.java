package com.oms.collector.repository;

import com.oms.collector.entity.RecordingVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RecordingVideoRepository extends JpaRepository<RecordingVideo, UUID> {

    List<RecordingVideo> findTop50ByInvoiceNoOrderByCreatedAtDesc(String invoiceNo);

    List<RecordingVideo> findTop50ByOrderNoOrderByCreatedAtDesc(String orderNo);

    List<RecordingVideo> findTop50ByInvoiceNoAndVideoUrlIsNotNullOrderByCreatedAtDesc(String invoiceNo);

    List<RecordingVideo> findTop50ByOrderNoAndVideoUrlIsNotNullOrderByCreatedAtDesc(String orderNo);
}
