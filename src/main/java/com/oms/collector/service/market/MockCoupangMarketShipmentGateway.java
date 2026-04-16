package com.oms.collector.service.market;

import org.springframework.stereotype.Component;

@Component
public class MockCoupangMarketShipmentGateway extends AbstractMockMarketShipmentGateway {
    @Override
    protected String getChannelCode() {
        return "COUPANG";
    }

    @Override
    protected String getChannelName() {
        return "쿠팡";
    }
}
