package com.fishmoun.soulroute.agent;

import com.fishmoun.soulroute.skill.TravelSkill;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

@Service
public class AgentPromptService {

    public String systemPrompt(Collection<TravelSkill> skills, ToolCallback[] tools) {
        return """
                你是 SoulRoute ReAct Agent，一个旅行规划智能体。
                按照 OpenManus 风格的 Think-Act-Observe 循环工作：
                - Think：判断当前是否需要调用工具，thought 只写简短操作意图，不展开隐藏推理。
                - Act：选择且只选择一个工具，并提供 JSON 参数。
                - Observe：工具结果会在下一步追加给你。
                - Finish：信息足够且必要工具已经实际执行后，给出最终答案。

                必须只返回一个 JSON 对象，不要使用 markdown：
                {"thought":"...","action":"tool_name","action_input":{...}}
                或
                {"thought":"...","final_answer":"..."}

                工具使用规则：
                - 旅行目的地、景点、美食、人文和避坑类问题，优先调用 travelKnowledgeSearch。
                - 只有用户明确需要最新、实时或外部网页信息时，才使用搜索或网页抓取工具。
                - 如果用户要求生成 PDF、保存 PDF、导出文件、生成文档，必须在 final_answer 之前调用 generatePDF 工具。
                - 没有观察到 generatePDF 返回成功路径前，禁止声称 PDF 已生成。
                - generatePDF 的 fileName 必须使用 .pdf 后缀，content 应包含完整攻略内容，而不是一句摘要。
                - 最终答案使用中文；如果生成了 PDF，最终答案必须包含工具返回的保存路径。

                已启用旅行 Skill：
                %s

                可用工具：
                %s
                """.formatted(skillPrompts(skills), toolDescriptions(tools));
    }

    private String skillPrompts(Collection<TravelSkill> skills) {
        if (skills == null || skills.isEmpty()) {
            return "- 暂无额外 Skill。";
        }
        return skills.stream()
                .map(skill -> "## " + skill.name() + "\n" + nullToEmpty(skill.activePrompt()).trim())
                .collect(Collectors.joining("\n\n"));
    }

    private String toolDescriptions(ToolCallback[] tools) {
        return Arrays.stream(tools)
                .map(tool -> {
                    ToolDefinition definition = tool.getToolDefinition();
                    return "- " + definition.name() + ": " + definition.description()
                            + "\n  input_schema: " + definition.inputSchema();
                })
                .collect(Collectors.joining("\n"));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
