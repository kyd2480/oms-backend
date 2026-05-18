package com.oms.collector.service.market;

import com.oms.collector.entity.Order;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractMockMarketShipmentGateway implements MarketShipmentGateway {
    @Override
    public MarketShipmentSyncService.MarketShipmentSyncResult sendShipment(
            Order order,
            String carrierCode,
            String carrierName,
            String trackingNo
    ) {
        log.info("[Mock Market Sync] channel={} orderNo={} channelOrderNo={} carrier={} tracking={}",
            getChannelCode(),
            order.getOrderNo(),
            order.getChannelOrderNo(),
            carrierCode,
            trackingNo
        );
        return MarketShipmentSyncService.MarketShipmentSyncResult.success(
            getChannelName() + " 발송완료 전송 성공"
        );
    }

    @Override
    public boolean supports(String channelCode) {
        if (channelCode == null || channelCode.isBlank()) {
            return false;
        }
        if (getChannelCode().equalsIgnoreCase(channelCode)) {
            return true;
        }
        if (channelCode.startsWith("SABANGNET_")) {
            return getChannelCode().equalsIgnoreCase(channelCode.substring("SABANGNET_".length()));
        }
        return false;
    }

    protected abstract String getChannelCode();

    protected abstract String getChannelName();
}
