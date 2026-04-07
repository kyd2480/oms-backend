package com.oms.collector.agent.dto;

import java.util.List;
import java.util.Map;

public record AgentChatResponse(
    boolean success,
    String answer,
    String model,
    boolean configured,
    List<Map<String, Object>> toolCalls,
    List<String> warnings
) {}
