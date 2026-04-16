package com.oms.collector.service.market;

import org.springframework.stereotype.Component;

@Component
public class MockNaverMarketShipmentGateway extends AbstractMockMarketShipmentGateway {
    @Override
    protected String getChannelCode() {
        return "NAVER";
    }

    @Override
    protected String getChannelName() {
        return "네이버 스마트스토어";
    }
}
