package com.fishmoun.soulroute.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class ToolCallAgent extends ReActAgent {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final Map<String, ToolCallback> tools;
    private final ToolCallback[] toolCallbacks;
    private final AgentPromptService promptService;

    protected ToolCallAgent(ChatModel chatModel,
                            ObjectMapper objectMapper,
                            ToolCallback[] tools,
                            AgentPromptService promptService,
                            int maxSteps) {
        super(maxSteps);
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.toolCallbacks = tools;
        this.promptService = promptService;
        this.tools = Arrays.stream(tools)
                .collect(Collectors.toMap(
                        tool -> tool.getToolDefinition().name(),
                        tool -> tool,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    @Override
    protected ReActDecision think(AgentContext context, int stepNumber) {
        String response = chatModel.call(new Prompt(
                new SystemMessage(promptService.systemPrompt(context.skills(), toolCallbacks)),
                new UserMessage(userPrompt(context, stepNumber))
        )).getResult().getOutput().getText();
        return parseDecision(response);
    }

    @Override
    protected String act(ReActDecision decision) {
        if (!StringUtils.hasText(decision.action())) {
            return "No action was provided.";
        }
        ToolCallback tool = tools.get(decision.action());
        if (tool == null) {
            return "Unknown tool: " + decision.action() + ". Available tools: " + String.join(", ", tools.keySet());
        }
        try {
            String result = tool.call(StringUtils.hasText(decision.actionInput()) ? decision.actionInput() : "{}");
            return truncate(result, 6000);
        } catch (Exception e) {
            return "Tool execution failed: " + e.getMessage();
        }
    }

    private String userPrompt(AgentContext context, int stepNumber) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("任务:\n").append(context.task()).append("\n\n");
        prompt.append("会话 ID: ").append(context.chatId()).append("\n");
        if (context.userId() != null) {
            prompt.append("用户 ID: ").append(context.userId()).append("\n");
        }
        if (context.preference() != null && !context.preference().toPromptText().isBlank()) {
            prompt.append("用户旅行偏好:\n").append(context.preference().toPromptText()).append("\n");
        }
        prompt.append("当前步骤: ").append(stepNumber).append("\n\n");
        if (!context.steps().isEmpty()) {
            prompt.append("历史步骤:\n");
            for (AgentStep step : context.steps()) {
                prompt.append("步骤 ").append(step.step()).append("\n")
                        .append("Thought: ").append(nullToEmpty(step.thought())).append("\n")
                        .append("Action: ").append(nullToEmpty(step.action())).append("\n")
                        .append("Action input: ").append(nullToEmpty(step.actionInput())).append("\n")
                        .append("Observation: ").append(truncate(nullToEmpty(step.observation()), 4000)).append("\n\n");
            }
        }
        prompt.append("返回下一步 JSON 决策。");
        return prompt.toString();
    }

    private ReActDecision parseDecision(String response) {
        String json = stripCodeFence(response);
        try {
            JsonNode node = objectMapper.readTree(json);
            String thought = text(node, "thought");
            String finalAnswer = text(node, "final_answer");
            String action = text(node, "action");
            JsonNode actionInputNode = node.get("action_input");
            String actionInput = actionInputNode == null || actionInputNode.isNull()
                    ? "{}"
                    : objectMapper.writeValueAsString(actionInputNode);
            return new ReActDecision(thought, action, actionInput, finalAnswer);
        } catch (JsonProcessingException e) {
            return new ReActDecision("Model returned non-JSON output.", null, null, response);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String stripCodeFence(String value) {
        String text = value == null ? "" : value.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*\\s*", "");
            text = text.replaceFirst("\\s*```$", "");
        }
        return text.trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "\n...[truncated]";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
