package com.oms.collector.agent.dto;

public record AgentChatMessage(
    String role,
    String content
) {}
