package com.oms.collector.dto;

import java.util.ArrayList;
import java.util.List;

public class ClaimRequest {
    public String source;
    public String channelCode;
    public String channelOrderNo;
    public String orderNo;
    public String claimType; // CANCEL | PARTIAL_CANCEL | RETURN | EXCHANGE
    public String claimReason;
    public String claimMemo;
    public String returnTrackingNo;
    public String carrierName;
    public String recipientName;
    public String recipientPhone;
    public String channelName;
    public List<ClaimItemRequest> items = new ArrayList<>();
}
