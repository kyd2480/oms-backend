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
        return getChannelCode().equalsIgnoreCase(channelCode);
    }

    protected abstract String getChannelCode();

    protected abstract String getChannelName();
}
