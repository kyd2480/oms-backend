package com.oms.collector.agent.dto;

import java.util.List;

public record AgentChatRequest(
    String message,
    String userName,
    List<AgentChatMessage> history
) {}
