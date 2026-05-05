package com.fishmoun.soulroute.controller;

import com.fishmoun.soulroute.app.TravelApp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/travel")
@Tag(name = "旅行规划对话接口", description = "SoulRoute 灵旅 AI 智能体对话能力")
public class TravelController {

    private final TravelApp travelApp;

    public TravelController(TravelApp travelApp) {
        this.travelApp = travelApp;
    }

    @PostMapping("/chat")
    @Operation(summary = "普通旅行规划对话")
    public ResponseEntity<?> chat(@RequestBody TravelChatRequest request) {
        RequestPayload payload = validateAndNormalize(request);
        if (payload.error() != null) {
            return payload.error();
        }
        return ResponseEntity.ok(travelApp.doChat(payload.message(), payload.chatId()));
    }

    @PostMapping("/chat-with-report")
    @Operation(summary = "生成结构化旅行攻略报告")
    public ResponseEntity<?> chatWithReport(@RequestBody TravelChatRequest request) {
        RequestPayload payload = validateAndNormalize(request);
        if (payload.error() != null) {
            return payload.error();
        }
        return ResponseEntity.ok(travelApp.doChatWithReport(payload.message(), payload.chatId()));
    }

    @PostMapping("/chat-with-rag")
    @Operation(summary = "知识库增强旅行规划对话")
    public ResponseEntity<?> chatWithRag(@RequestBody TravelChatRequest request) {
        RequestPayload payload = validateAndNormalize(request);
        if (payload.error() != null) {
            return payload.error();
        }
        return ResponseEntity.ok(travelApp.doChatWithRag(payload.message(), payload.chatId()));
    }

    @PostMapping("/chat-with-tools")
    @Operation(summary = "工具调用增强旅行规划对话")
    public ResponseEntity<?> chatWithTools(@RequestBody TravelChatRequest request) {
        RequestPayload payload = validateAndNormalize(request);
        if (payload.error() != null) {
            return payload.error();
        }
        return ResponseEntity.ok(travelApp.doChatWithTools(payload.message(), payload.chatId()));
    }

    @PostMapping("/chat-with-mcp")
    @Operation(summary = "MCP 扩展工具旅行规划对话")
    public ResponseEntity<?> chatWithMcp(@RequestBody TravelChatRequest request) {
        RequestPayload payload = validateAndNormalize(request);
        if (payload.error() != null) {
            return payload.error();
        }
        return ResponseEntity.ok(travelApp.doChatWithMcp(payload.message(), payload.chatId()));
    }

    private RequestPayload validateAndNormalize(TravelChatRequest request) {
        if (request == null || !StringUtils.hasText(request.message())) {
            return new RequestPayload(null, null,
                    ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("message 不能为空")));
        }
        String chatId = StringUtils.hasText(request.chatId())
                ? request.chatId()
                : "travel-" + UUID.randomUUID();
        return new RequestPayload(request.message().trim(), chatId, null);
    }

    public record TravelChatRequest(String message, String chatId) {
    }

    public record ErrorResponse(String message) {
    }

    private record RequestPayload(String message, String chatId, ResponseEntity<ErrorResponse> error) {
    }
}
