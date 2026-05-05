package com.fishmoun.soulroute.controller;

import com.fishmoun.soulroute.agent.AgentRunResult;
import com.fishmoun.soulroute.agent.SoulRouteReActAgent;
import com.fishmoun.soulroute.app.TravelApp;
import com.fishmoun.soulroute.auth.AuthUser;
import com.fishmoun.soulroute.auth.CurrentUserService;
import com.fishmoun.soulroute.constant.FileConstant;
import com.fishmoun.soulroute.conversation.ConversationHistoryService;
import com.fishmoun.soulroute.evolution.ReactRunRecord;
import com.fishmoun.soulroute.evolution.ReactRunTraceService;
import com.fishmoun.soulroute.evolution.SkillEvolutionEngine;
import com.fishmoun.soulroute.preference.PreferenceLearningService;
import com.fishmoun.soulroute.preference.TravelPreference;
import com.fishmoun.soulroute.preference.TravelPreferenceService;
import com.fishmoun.soulroute.skill.TravelSkill;
import com.fishmoun.soulroute.skill.TravelSkillService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/travel")
@Tag(name = "旅行规划对话接口", description = "SoulRoute 灵旅 AI 智能体对话能力")
public class TravelController {

    private final TravelApp travelApp;
    private final SoulRouteReActAgent soulRouteReActAgent;
    private final ConversationHistoryService conversationHistoryService;
    private final CurrentUserService currentUserService;
    private final TravelPreferenceService preferenceService;
    private final PreferenceLearningService preferenceLearningService;
    private final TravelSkillService skillService;
    private final ReactRunTraceService traceService;
    private final SkillEvolutionEngine evolutionEngine;
    private final ObjectMapper objectMapper;

    public TravelController(TravelApp travelApp,
                            SoulRouteReActAgent soulRouteReActAgent,
                            ConversationHistoryService conversationHistoryService,
                            CurrentUserService currentUserService,
                            TravelPreferenceService preferenceService,
                            PreferenceLearningService preferenceLearningService,
                            TravelSkillService skillService,
                            ReactRunTraceService traceService,
                            SkillEvolutionEngine evolutionEngine,
                            ObjectMapper objectMapper) {
        this.travelApp = travelApp;
        this.soulRouteReActAgent = soulRouteReActAgent;
        this.conversationHistoryService = conversationHistoryService;
        this.currentUserService = currentUserService;
        this.preferenceService = preferenceService;
        this.preferenceLearningService = preferenceLearningService;
        this.skillService = skillService;
        this.traceService = traceService;
        this.evolutionEngine = evolutionEngine;
        this.objectMapper = objectMapper;
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

    @PostMapping("/react")
    @Operation(summary = "ReAct 智能体聚合旅行规划能力")
    public ResponseEntity<?> react(HttpServletRequest servletRequest, @RequestBody TravelChatRequest request) {
        RequestPayload payload = validateAndNormalize(request);
        if (payload.error() != null) {
            return payload.error();
        }
        UserRuntime runtime = runtime(servletRequest);
        String task = buildReactTask(payload, runtime.userId());
        conversationHistoryService.appendMessage(runtime.userId(), payload.chatId(), "user", payload.message(), "ReAct");
        AgentRunResult result = soulRouteReActAgent.run(
                task,
                payload.chatId(),
                runtime.userId(),
                runtime.preference().orElse(null),
                runtime.skills(),
                null
        );
        conversationHistoryService.appendMessage(runtime.userId(), payload.chatId(), "assistant", result.answer(), "ReAct");
        learnPreference(runtime.userId(), payload.message(), result.answer());
        persistTraceAndEvolve(runtime.userId(), payload.chatId(), task, result);
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/react/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "ReAct 智能体实时步骤流")
    public SseEmitter reactStream(HttpServletRequest servletRequest, @RequestBody TravelChatRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        RequestPayload payload = validateAndNormalize(request);
        if (payload.error() != null) {
            sendValidationError(emitter, "message 不能为空");
            return emitter;
        }
        UserRuntime runtime = runtime(servletRequest);

        CompletableFuture.runAsync(() -> {
            try {
                String task = buildReactTask(payload, runtime.userId());
                conversationHistoryService.appendMessage(runtime.userId(), payload.chatId(), "user", payload.message(), "ReAct");
                emitter.send(SseEmitter.event()
                        .name("status")
                        .data(new AgentStatusEvent("thinking", "智能体开始思考", payload.chatId())));

                AgentRunResult result = soulRouteReActAgent.run(
                        task,
                        payload.chatId(),
                        runtime.userId(),
                        runtime.preference().orElse(null),
                        runtime.skills(),
                        step -> {
                    try {
                        String json = objectMapper.writer()
                            .without(SerializationFeature.INDENT_OUTPUT)
                            .writeValueAsString(step);
                        json = json.replace("\r\n", "\\\\n").replace("\n", "\\\\n").replace("\r", "\\\\n");
                        emitter.send(SseEmitter.event().name("step").data(json));
                    } catch (JsonProcessingException e) {
                        try {
                            emitter.send(SseEmitter.event().name("error").data(new ErrorResponse("序列化 step 失败")));
                        } catch (Exception ignored) {
                        }
                        throw new IllegalStateException("SSE client disconnected or serialization failed");
                    } catch (Exception ignored) {
                        throw new IllegalStateException("SSE client disconnected");
                    }
                });

                conversationHistoryService.appendMessage(runtime.userId(), payload.chatId(), "assistant", result.answer(), "ReAct");
                learnPreference(runtime.userId(), payload.message(), result.answer());
                persistTraceAndEvolve(runtime.userId(), payload.chatId(), task, result);
                try {
                        String finalJson = objectMapper.writer()
                            .without(SerializationFeature.INDENT_OUTPUT)
                            .writeValueAsString(result);
                        finalJson = finalJson.replace("\r\n", "\\\\n").replace("\n", "\\\\n").replace("\r", "\\\\n");
                        emitter.send(SseEmitter.event().name("final").data(finalJson));
                } catch (JsonProcessingException e) {
                    try {
                        emitter.send(SseEmitter.event().name("final").data(result));
                    } catch (Exception ignored) {
                    }
                }
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(new ErrorResponse("实时输出失败：" + e.getMessage())));
                } catch (Exception ignored) {
                    // client disconnected
                }
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    @GetMapping("/conversations")
    @Operation(summary = "获取历史会话列表")
    public ResponseEntity<?> conversations(HttpServletRequest servletRequest) {
        Long userId = runtime(servletRequest).userId();
        return ResponseEntity.ok(conversationHistoryService.listConversations(userId));
    }

    @GetMapping("/conversations/{chatId}")
    @Operation(summary = "获取历史会话详情")
    public ResponseEntity<?> conversation(HttpServletRequest servletRequest, @PathVariable String chatId) {
        Long userId = runtime(servletRequest).userId();
        return ResponseEntity.ok(conversationHistoryService.getConversation(userId, chatId));
    }

    @DeleteMapping("/conversations/{chatId}")
    @Operation(summary = "删除历史会话")
    public ResponseEntity<?> deleteConversation(HttpServletRequest servletRequest, @PathVariable String chatId) {
        Long userId = runtime(servletRequest).userId();
        conversationHistoryService.clearConversation(userId, chatId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/files/pdf/{fileName:.+}")
    @Operation(summary = "下载已生成的 PDF 文件")
    public ResponseEntity<?> downloadPdf(@PathVariable String fileName) {
        if (!StringUtils.hasText(fileName)
                || fileName.contains("..")
                || fileName.contains("/")
                || fileName.contains("\\")
                || !fileName.toLowerCase().endsWith(".pdf")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("非法文件名"));
        }
        Path pdfDir = Path.of(FileConstant.FILE_SAVE_DIR, "pdf").toAbsolutePath().normalize();
        Path pdfFile = pdfDir.resolve(fileName).normalize();
        if (!pdfFile.startsWith(pdfDir) || !Files.isRegularFile(pdfFile)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("PDF 文件不存在"));
        }
        Resource resource = new FileSystemResource(pdfFile);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(resource);
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

    private String buildReactTask(RequestPayload payload, Long userId) {
        String context = conversationHistoryService.buildContext(userId, payload.chatId());
        return context.isBlank()
                ? payload.message()
                : context + "\n最新问题:\n" + payload.message();
    }

    private UserRuntime runtime(HttpServletRequest servletRequest) {
        Optional<AuthUser> user = currentUserService.currentUser(servletRequest);
        Long userId = user.map(AuthUser::id).orElse(null);
        Optional<TravelPreference> preference = userId == null ? Optional.empty() : preferenceService.findByUserId(userId);
        return new UserRuntime(userId, preference, skillService.activeSkills());
    }

    private void persistTraceAndEvolve(Long userId, String chatId, String task, AgentRunResult result) {
        ReactRunRecord run = traceService.save(userId, chatId, task, result);
        evolutionEngine.evolveFromRun(run);
    }

    private void learnPreference(Long userId, String userMessage, String assistantAnswer) {
        preferenceLearningService.learnFromConversation(userId, userMessage, assistantAnswer);
    }

    private void sendValidationError(SseEmitter emitter, String message) {
        CompletableFuture.runAsync(() -> {
            try {
                emitter.send(SseEmitter.event().name("error").data(new ErrorResponse(message)));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
    }

    public record TravelChatRequest(String message, String chatId) {
    }

    public record ErrorResponse(String message) {
    }

    public record AgentStatusEvent(String status, String message, String chatId) {
    }

    private record RequestPayload(String message, String chatId, ResponseEntity<ErrorResponse> error) {
    }

    private record UserRuntime(Long userId, Optional<TravelPreference> preference, List<TravelSkill> skills) {
    }
}
