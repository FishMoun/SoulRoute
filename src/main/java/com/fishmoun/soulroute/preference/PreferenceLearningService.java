package com.fishmoun.soulroute.preference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class PreferenceLearningService {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final TravelPreferenceService preferenceService;
    private final boolean enabled;

    public PreferenceLearningService(ChatModel dashscopeChatModel,
                                     ObjectMapper objectMapper,
                                     TravelPreferenceService preferenceService,
                                     @Value("${soulroute.preference.learning.enabled:true}") boolean enabled) {
        this.chatModel = dashscopeChatModel;
        this.objectMapper = objectMapper;
        this.preferenceService = preferenceService;
        this.enabled = enabled;
    }

    public void learnFromConversation(Long userId, String userMessage, String assistantAnswer) {
        if (!enabled || userId == null || !StringUtils.hasText(userMessage)) {
            return;
        }
        try {
            TravelPreference existing = preferenceService.findByUserId(userId).orElse(TravelPreference.empty());
            TravelPreference learned = extract(existing, userMessage, assistantAnswer);
            TravelPreference merged = merge(existing, learned);
            if (merged.hasAnyPreference()) {
                preferenceService.save(userId, merged);
            }
        } catch (Exception e) {
            log.warn("用户旅行偏好自动总结失败，userId={}", userId, e);
        }
    }

    private TravelPreference extract(TravelPreference existing, String userMessage, String assistantAnswer) throws Exception {
        String response = chatModel.call(new Prompt(
                new SystemMessage(systemPrompt()),
                new UserMessage("""
                        已有用户偏好：
                        %s

                        本轮用户输入：
                        %s

                        本轮助手回答摘要：
                        %s
                        """.formatted(existing.toPromptText(), userMessage, truncate(assistantAnswer, 1200)))
        )).getResult().getOutput().getText();
        JsonNode node = objectMapper.readTree(stripCodeFence(response));
        return new TravelPreference(
                text(node, "departureCity"),
                text(node, "budgetLevel"),
                text(node, "travelPace"),
                text(node, "companionType"),
                text(node, "interests"),
                text(node, "dietaryRestrictions"),
                text(node, "accommodationPreference"),
                text(node, "extraNotes"),
                null
        );
    }

    private TravelPreference merge(TravelPreference existing, TravelPreference learned) {
        return new TravelPreference(
                choose(existing.departureCity(), learned.departureCity()),
                choose(existing.budgetLevel(), learned.budgetLevel()),
                choose(existing.travelPace(), learned.travelPace()),
                choose(existing.companionType(), learned.companionType()),
                mergeListText(existing.interests(), learned.interests()),
                mergeListText(existing.dietaryRestrictions(), learned.dietaryRestrictions()),
                choose(existing.accommodationPreference(), learned.accommodationPreference()),
                mergeListText(existing.extraNotes(), learned.extraNotes()),
                existing.updatedAt()
        );
    }

    private String systemPrompt() {
        return """
                你是用户旅行偏好总结器。请从对话中提取“稳定、可复用”的用户旅行偏好，不要提取一次性目的地细节。
                只返回 JSON，不要 markdown，不要解释。
                字段：
                {
                  "departureCity": "常用出发地或 null",
                  "budgetLevel": "预算偏好，如 经济/中等/舒适/高端 或 null",
                  "travelPace": "轻松/适中/紧凑 或 null",
                  "companionType": "独行/情侣/亲子/朋友/家庭/老人 等或 null",
                  "interests": "稳定兴趣偏好，用中文逗号分隔，如 美食，人文，拍照",
                  "dietaryRestrictions": "饮食限制，用中文逗号分隔或 null",
                  "accommodationPreference": "住宿偏好或 null",
                  "extraNotes": "其他长期偏好或 null"
                }
                规则：
                - “我这次去上海三天”不是长期偏好，不要写入 departureCity 或 interests。
                - “我喜欢轻松一点”“以后都别太赶”“我不吃辣”“我喜欢美食和人文”是长期偏好。
                - 如果无法确定长期偏好，对应字段返回 null。
                - 不要覆盖已有偏好，除非本轮用户明确表达了更具体的长期偏好。
                """;
    }

    private String choose(String existing, String learned) {
        return StringUtils.hasText(existing) ? existing.trim() : clean(learned);
    }

    private String mergeListText(String existing, String learned) {
        if (!StringUtils.hasText(existing)) {
            return clean(learned);
        }
        if (!StringUtils.hasText(learned)) {
            return existing.trim();
        }
        String value = existing.trim();
        for (String item : learned.split("[,，、;；]")) {
            String normalized = item.trim();
            if (StringUtils.hasText(normalized) && !value.contains(normalized)) {
                value += "，" + normalized;
            }
        }
        return value;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return clean(value.asText());
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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
}
