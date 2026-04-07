package com.oms.collector.agent.dto;

import java.util.Map;

public record AgentActionProposal(
    String actionType,
    String title,
    String description,
    String confirmationToken,
    boolean requiresConfirmation,
    String riskLevel,
    Map<String, String> params
) {}
