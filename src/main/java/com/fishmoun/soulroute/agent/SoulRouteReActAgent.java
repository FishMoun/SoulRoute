package com.fishmoun.soulroute.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SoulRouteReActAgent extends ToolCallAgent {

    public SoulRouteReActAgent(ChatModel dashscopeChatModel,
                               ObjectMapper objectMapper,
                               ToolCallback[] allTools,
                               AgentPromptService promptService,
                               @Value("${soulroute.agent.max-steps:12}") int maxSteps) {
        super(dashscopeChatModel, objectMapper, allTools, promptService, maxSteps);
    }
}
