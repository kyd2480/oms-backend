package com.oms.collector.agent;

import com.oms.collector.agent.dto.AgentChatRequest;
import com.oms.collector.agent.dto.AgentChatResponse;
import lombok.RequiredArgsConstructor;
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

    @PostMapping("/chat")
    public ResponseEntity<AgentChatResponse> chat(@RequestBody AgentChatRequest request) {
        return ResponseEntity.ok(omsAgentService.chat(request));
    }
}
