package com.oms.collector.service.market;

import org.springframework.stereotype.Component;

@Component
public class Mock11StMarketShipmentGateway extends AbstractMockMarketShipmentGateway {
    @Override
    protected String getChannelCode() {
        return "11ST";
    }

    @Override
    protected String getChannelName() {
        return "11번가";
    }
}
