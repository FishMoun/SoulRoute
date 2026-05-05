package com.fishmoun.soulroute.rag;

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

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class QueryUnderstandingService {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public QueryUnderstandingService(ChatModel dashscopeChatModel,
                                     ObjectMapper objectMapper,
                                     @Value("${soulroute.rag.query-understanding.enabled:true}") boolean enabled) {
        this.chatModel = dashscopeChatModel;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    public QueryUnderstanding understand(String query) {
        if (!enabled || !StringUtils.hasText(query)) {
            return QueryUnderstanding.empty();
        }
        try {
            String response = chatModel.call(new Prompt(
                    new SystemMessage(systemPrompt()),
                    new UserMessage("用户问题：" + query)
            )).getResult().getOutput().getText();
            return parse(response);
        } catch (Exception e) {
            log.warn("LLM 查询理解失败，回退到规则解析。query={}", query, e);
            return QueryUnderstanding.empty();
        }
    }

    private String systemPrompt() {
        return """
                你是旅行 RAG 的查询理解器。只抽取可用于检索的结构化槽位，不回答用户问题。
                必须只返回 JSON，不要 markdown，不要解释。
                字段：
                {
                  "rewrittenQuery": "更适合检索的中文短查询",
                  "destinations": ["用户明确或隐含的目的地"],
                  "expandedDestinations": ["对模糊区域、别名、省份、主题目的地扩展出的核心地点"],
                  "days": 旅行天数数字或 null,
                  "budget": "预算描述或 null",
                  "pace": "轻松/适中/紧凑 或 null",
                  "companion": "独行/情侣/亲子/朋友/家庭/老人 等或 null",
                  "interests": ["景点","美食","人文","亲子","住宿","交通","避坑","购物","路线" 等主题词],
                  "outputFormat": "攻略/PDF/清单/路线/问答 等或 null"
                }
                规则：
                - 不确定的字段返回 null 或空数组。
                - “江南、川西、海边城市、古镇、草原”等模糊表达可以放入 destinations，并在 expandedDestinations 给出少量高相关地点。
                - expandedDestinations 最多 8 个，优先选择旅行攻略库常见城市。
                - rewrittenQuery 保持简短，包含目的地、天数、兴趣和输出形式。
                """;
    }

    private QueryUnderstanding parse(String response) throws Exception {
        JsonNode node = objectMapper.readTree(stripCodeFence(response));
        return new QueryUnderstanding(
                text(node, "rewrittenQuery"),
                list(node, "destinations"),
                list(node, "expandedDestinations"),
                node.hasNonNull("days") ? node.get("days").asInt() : null,
                text(node, "budget"),
                text(node, "pace"),
                text(node, "companion"),
                list(node, "interests"),
                text(node, "outputFormat")
        );
    }

    private List<String> list(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : value) {
            String text = item.asText(null);
            if (StringUtils.hasText(text)) {
                result.add(text.trim());
            }
        }
        return result;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private String stripCodeFence(String value) {
        String text = value == null ? "" : value.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*\\s*", "");
            text = text.replaceFirst("\\s*```$", "");
        }
        return text.trim();
    }
}
