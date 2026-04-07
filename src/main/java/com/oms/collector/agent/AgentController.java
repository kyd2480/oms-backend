package com.oms.collector.agent;

import com.oms.collector.agent.dto.AgentExecuteRequest;
import com.oms.collector.agent.dto.AgentExecuteResponse;
import com.oms.collector.agent.dto.AgentExportRequest;
import com.oms.collector.agent.dto.AgentChatRequest;
import com.oms.collector.agent.dto.AgentChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AgentController {

    private final OmsAgentService omsAgentService;
    private final AgentActionService agentActionService;
    private final AgentExportService agentExportService;

    @PostMapping("/chat")
    public ResponseEntity<AgentChatResponse> chat(@RequestBody AgentChatRequest request) {
        return ResponseEntity.ok(omsAgentService.chat(request));
    }

    @PostMapping("/execute")
    public ResponseEntity<AgentExecuteResponse> execute(@RequestBody AgentExecuteRequest request) {
        return ResponseEntity.ok(agentActionService.execute(request.confirmationToken()));
    }

    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@RequestBody AgentExportRequest request) throws Exception {
        byte[] file = agentExportService.export(request);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"oms-agent-export.xlsx\"")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(file);
    }
}
