package com.oms.collector.service.market;

import com.oms.collector.entity.Order;

public interface MarketShipmentGateway {
    boolean supports(String channelCode);

    MarketShipmentSyncService.MarketShipmentSyncResult sendShipment(Order order, String carrierCode, String carrierName, String trackingNo);
}
