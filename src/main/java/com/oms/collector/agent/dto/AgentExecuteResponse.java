package com.oms.collector.agent.dto;

public record AgentExecuteResponse(
    boolean success,
    String message,
    String actionType
) {}
