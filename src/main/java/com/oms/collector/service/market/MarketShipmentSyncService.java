package com.oms.collector.service.market;

import com.oms.collector.entity.Order;
import com.oms.collector.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketShipmentSyncService {
    private final List<MarketShipmentGateway> gateways;
    private final OrderRepository orderRepository;

    public record MarketShipmentSyncResult(boolean success, String message) {
        public static MarketShipmentSyncResult success(String message) {
            return new MarketShipmentSyncResult(true, message);
        }

        public static MarketShipmentSyncResult failed(String message) {
            return new MarketShipmentSyncResult(false, message);
        }
    }

    public MarketShipmentSyncResult syncShipment(Order order, String carrierCode, String carrierName, String trackingNo) {
        order.setMarketSyncAttemptedAt(LocalDateTime.now());

        if (order.getChannel() == null || order.getChannel().getChannelCode() == null || order.getChannel().getChannelCode().isBlank()) {
            order.setMarketSyncStatus(Order.MarketSyncStatus.NOT_REQUIRED);
            order.setMarketSyncMessage("판매처 정보가 없는 주문");
            order.setMarketSyncedAt(null);
            orderRepository.save(order);
            return MarketShipmentSyncResult.success("판매처 연동 대상이 아닌 주문");
        }

        if (order.getChannelOrderNo() == null || order.getChannelOrderNo().isBlank()) {
            order.setMarketSyncStatus(Order.MarketSyncStatus.FAILED);
            order.setMarketSyncMessage("판매처 주문번호(channelOrderNo)가 없어 발송완료 전송 불가");
            order.setMarketSyncedAt(null);
            orderRepository.save(order);
            return MarketShipmentSyncResult.failed(order.getMarketSyncMessage());
        }

        String channelCode = order.getChannel().getChannelCode();
        MarketShipmentGateway gateway = gateways.stream()
            .filter(it -> it.supports(channelCode))
            .findFirst()
            .orElse(null);

        if (gateway == null) {
            order.setMarketSyncStatus(Order.MarketSyncStatus.FAILED);
            order.setMarketSyncMessage(channelCode + " 판매처 발송 API 미구현");
            order.setMarketSyncedAt(null);
            orderRepository.save(order);
            return MarketShipmentSyncResult.failed(order.getMarketSyncMessage());
        }

        try {
            MarketShipmentSyncResult result = gateway.sendShipment(order, carrierCode, carrierName, trackingNo);
            if (result.success()) {
                order.setMarketSyncStatus(Order.MarketSyncStatus.SUCCESS);
                order.setMarketSyncMessage(result.message());
                order.setMarketSyncedAt(LocalDateTime.now());
            } else {
                order.setMarketSyncStatus(Order.MarketSyncStatus.FAILED);
                order.setMarketSyncMessage(result.message());
                order.setMarketSyncedAt(null);
            }
            orderRepository.save(order);
            return result;
        } catch (Exception e) {
            log.error("판매처 발송완료 전송 실패: orderNo={} channel={}", order.getOrderNo(), channelCode, e);
            order.setMarketSyncStatus(Order.MarketSyncStatus.FAILED);
            order.setMarketSyncMessage(e.getMessage() != null ? e.getMessage() : "판매처 발송완료 전송 중 예외 발생");
            order.setMarketSyncedAt(null);
            orderRepository.save(order);
            return MarketShipmentSyncResult.failed(order.getMarketSyncMessage());
        }
    }
}
