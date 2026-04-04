package com.fishmoun.soulroute.app;

import com.fishmoun.soulroute.advisor.MyLoggerAdvisor;
import com.fishmoun.soulroute.chatmemory.FileBasedChatMemory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

@Component
@Slf4j
public class TravelApp {

    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = "扮演资深旅行规划师与目的地攻略专家。开场向用户表明身份，告知用户可咨询出行计划、路线安排、预算控制、住宿交通、美食打卡与避坑建议等旅行问题。" +
            "围绕自由行、情侣出游、家庭出游、朋友结伴、商务差旅五种场景提问：" +
            "自由行重点询问目的地、出发地、旅行天数、预算范围、兴趣偏好以及是否偏向轻松游或深度游；" +
            "情侣出游重点询问是否偏爱浪漫氛围、拍照打卡、特色住宿、美食体验及行程节奏；" +
            "家庭出游重点询问老人、小孩同行情况，是否注重舒适度、交通便利性、安全性与亲子项目；" +
            "朋友结伴重点询问人数、游玩偏好、是否注重娱乐项目、夜生活、性价比及分工安排；" +
            "商务差旅重点询问出差城市、停留时间、会议地点、可自由活动时段及是否需要兼顾高效出行与短途体验。" +
            "引导用户详细说明出发时间、目的地、同行人员、预算、交通方式、住宿要求、饮食偏好、想去的景点以及不想踩雷的点，" +
            "以便给出专属旅行方案。" +
            "在输出结果时，优先给出结构化建议，包括每日行程安排、景点游玩顺序、交通衔接、住宿区域建议、美食推荐、预算拆分、必备物品清单和注意事项。" +
            "若用户信息不足，主动追问关键细节；若用户时间有限，优先生成高效路线；若用户预算有限，优先推荐高性价比方案；若用户已有目标地点，重点补充玩法亮点与避坑建议。";


    public TravelApp(ChatModel dashscopeChatModel) {
        // 初始化基于内存的对话记忆
        String fileDir = System.getProperty("user.dir") + "/chat-memory";
        ChatMemory chatMemory = new FileBasedChatMemory(fileDir);
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(chatMemory)
                )
                .build();

    }

    public String doChat(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }
    public TravelReport doChatWithReport(String message, String chatId) {
        TravelReport travelReport = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "每次对话后都要生成旅行规划结果，标题为{用户名}的旅行攻略，内容为行程建议列表、预算建议、注意事项。")
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .call()
                .entity(TravelReport.class);
        log.info("travelReport: {}", travelReport);
        return travelReport;
    }

    public record TravelReport(String title, List<String> suggestions) {
    }



    @Resource
    private Advisor travelAppRagCloudAdvisor;

    public String doChatWithRag(String message, String chatId) {




        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                // 应用增强检索服务（云知识库服务）
                .advisors(travelAppRagCloudAdvisor)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    @Resource
    private ToolCallback[] allTools;

    public String doChatWithTools(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .tools(allTools)
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    public String doChatWithMcp(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .tools(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }


}

