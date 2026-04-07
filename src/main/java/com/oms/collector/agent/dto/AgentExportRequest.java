package com.oms.collector.agent.dto;

import java.util.List;
import java.util.Map;

public record AgentExportRequest(
    String title,
    List<Map<String, Object>> toolCalls
) {}
