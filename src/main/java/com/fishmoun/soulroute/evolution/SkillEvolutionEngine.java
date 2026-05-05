package com.fishmoun.soulroute.evolution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishmoun.soulroute.skill.SkillEvolutionRequest;
import com.fishmoun.soulroute.skill.TravelSkillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SkillEvolutionEngine {

    private static final double AUTO_ACTIVATE_THRESHOLD = 0.75;

    private final TravelSkillService skillService;
    private final ReactRunTraceService traceService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public SkillEvolutionEngine(TravelSkillService skillService,
                                ReactRunTraceService traceService,
                                JdbcTemplate jdbcTemplate,
                                ObjectMapper objectMapper) {
        this.skillService = skillService;
        this.traceService = traceService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void evolveFromRun(ReactRunRecord run) {
        if (run == null || run.failure() == null || !run.failure().failed()) {
            return;
        }
        try {
            String proposal = generateProposal(run.failure());
            if (!StringUtils.hasText(proposal)) {
                return;
            }
            SkillEvolutionRequest request = new SkillEvolutionRequest(
                    "travel-planning",
                    "react-run:" + run.id(),
                    run.failure().type() + "\n" + run.failure().signal(),
                    proposal
            );
            TravelSkillService.DraftSkillVersion draft = skillService.createDraftVersion(run.userId(), request);
            traceService.attachSkillVersion(run.id(), draft.versionId());
            SkillEvaluationResult evaluation = evaluate(draft.skillId(), draft.versionId(), proposal, run.failure().type());
            skillService.saveEvaluation(
                    draft.skillId(),
                    draft.versionId(),
                    "historical-react-runs",
                    evaluation.score(),
                    evaluation.passed(),
                    evaluation.metricsJson()
            );
            if (evaluation.passed()) {
                skillService.activateVersion(
                        draft.skillId(),
                        draft.versionId(),
                        "自动评估通过，来源 ReAct 运行 " + run.id() + "，失败类型 " + run.failure().type()
                );
            }
        } catch (Exception e) {
            log.warn("自动 Skill 沉淀失败，runId={}", run.id(), e);
        }
    }

    private String generateProposal(FailurePattern failure) {
        String type = failure.type();
        if ("PDF_FAILURE".equals(type)) {
            return """
                    PDF 生成可靠性 Skill：
                    - 生成 PDF 前先清理内容中的不可见控制字符、异常符号和可能导致字体渲染失败的字符。
                    - PDF 内容应优先使用纯文本结构化排版，图片只使用与目的地明确相关且可访问的真实图片。
                    - generatePDF 工具失败后，应根据错误信息简化内容并重试一次；成功前不要宣称 PDF 已生成。
                    - 最终答案必须返回可下载文件名或保存路径。
                    """;
        }
        if ("RETRIEVAL_WEAK".equals(type)) {
            return """
                    RAG 检索修正 Skill：
                    - 当本地知识库检索结果为空、不相关或用户指出检索不准时，应重写查询并补充目的地别名、城市/省份扩展词和旅行主题词。
                    - 如果用户问题包含省份，应同时检索省份名、核心城市名和具体玩法标签。
                    - 最终答案需要说明依据来自哪些本地知识片段；信息不足时先补充检索，不要直接编造。
                    """;
        }
        if ("MAX_STEPS".equals(type)) {
            return """
                    ReAct 步骤控制 Skill：
                    - 任务开始时先判断是否必须调用工具，避免重复检索同一信息。
                    - PDF 任务最多按“检索知识、整理内容、生成 PDF、返回结果”四类步骤推进。
                    - 当已有足够观察结果时应尽快 Finish，避免无效工具循环。
                    """;
        }
        if ("USER_CORRECTION".equals(type)) {
            return """
                    用户纠错吸收 Skill：
                    - 当用户指出“不对、不符合、重新、纠正、图片不符合实际”等反馈时，应优先承认并修正具体问题。
                    - 后续同类任务应把用户纠错转化为约束，例如目的地一致性、图片真实性、行程节奏和输出格式要求。
                    - 生成文件类结果时，需特别检查图片、标题、目的地和正文是否一致。
                    """;
        }
        if ("TOOL_FAILURE".equals(type)) {
            return """
                    工具失败恢复 Skill：
                    - 工具调用失败时，应读取错误信息并调整参数后最多重试一次。
                    - 不知道工具名时，应从可用工具列表中选择语义最接近的工具，不要重复调用未知工具。
                    - 失败仍无法恢复时，最终答案要说明失败原因和已完成的信息。
                    """;
        }
        if ("AGENT_ERROR".equals(type)) {
            return """
                    智能体异常恢复 Skill：
                    - 当模型输出非 JSON、工具异常或运行状态错误时，应回到 ReAct JSON 格式继续执行。
                    - thought 保持简短操作意图，action_input 必须是合法 JSON。
                    - 如果无法继续执行，应返回可解释的中文失败信息。
                    """;
        }
        return null;
    }

    private SkillEvaluationResult evaluate(Long skillId, Long versionId, String proposal, String failureType) {
        List<String> historicalSignals = jdbcTemplate.query("""
                        SELECT failure_signal
                        FROM react_runs
                        WHERE failed = true AND failure_type = ?
                        ORDER BY created_at DESC
                        LIMIT 20
                        """,
                (rs, rowNum) -> rs.getString("failure_signal"),
                failureType);
        int matchedSignals = 0;
        for (String signal : historicalSignals) {
            if (coversSignal(proposal, signal, failureType)) {
                matchedSignals++;
            }
        }
        double coverage = historicalSignals.isEmpty() ? 0.6 : (double) matchedSignals / historicalSignals.size();
        double structureScore = structureScore(proposal);
        double typeScore = typeKeywordScore(proposal, failureType);
        double score = round(coverage * 0.45 + structureScore * 0.25 + typeScore * 0.30);
        boolean passed = score >= AUTO_ACTIVATE_THRESHOLD;
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("skillId", skillId);
        metrics.put("versionId", versionId);
        metrics.put("failureType", failureType);
        metrics.put("historicalSamples", historicalSignals.size());
        metrics.put("matchedSignals", matchedSignals);
        metrics.put("coverage", round(coverage));
        metrics.put("structureScore", round(structureScore));
        metrics.put("typeScore", round(typeScore));
        metrics.put("threshold", AUTO_ACTIVATE_THRESHOLD);
        metrics.put("passed", passed);
        return new SkillEvaluationResult(score, passed, toJson(metrics));
    }

    private boolean coversSignal(String proposal, String signal, String failureType) {
        String text = safe(proposal) + "\n" + safe(signal);
        if ("PDF_FAILURE".equals(failureType)) {
            return containsAny(text, "PDF", "generatePDF", "字体", "图片", "重试", "文件");
        }
        if ("RETRIEVAL_WEAK".equals(failureType)) {
            return containsAny(text, "检索", "知识库", "重写", "扩展", "省份", "城市");
        }
        if ("MAX_STEPS".equals(failureType)) {
            return containsAny(text, "步骤", "Finish", "重复", "循环", "工具");
        }
        if ("USER_CORRECTION".equals(failureType)) {
            return containsAny(text, "纠错", "修正", "不符合", "图片", "一致");
        }
        if ("TOOL_FAILURE".equals(failureType)) {
            return containsAny(text, "工具", "失败", "重试", "参数");
        }
        return safe(proposal).length() > 80;
    }

    private double structureScore(String proposal) {
        String text = safe(proposal);
        int bulletCount = text.split("\\n- ").length - 1;
        double lengthScore = text.length() >= 80 ? 1.0 : text.length() / 80.0;
        double bulletScore = Math.min(1.0, bulletCount / 3.0);
        return (lengthScore + bulletScore) / 2.0;
    }

    private double typeKeywordScore(String proposal, String failureType) {
        String text = safe(proposal);
        if ("PDF_FAILURE".equals(failureType)) {
            return keywordRatio(text, "PDF", "generatePDF", "重试", "文件");
        }
        if ("RETRIEVAL_WEAK".equals(failureType)) {
            return keywordRatio(text, "检索", "重写", "扩展", "知识库");
        }
        if ("MAX_STEPS".equals(failureType)) {
            return keywordRatio(text, "步骤", "工具", "Finish", "循环");
        }
        if ("USER_CORRECTION".equals(failureType)) {
            return keywordRatio(text, "用户", "纠错", "修正", "一致");
        }
        if ("TOOL_FAILURE".equals(failureType)) {
            return keywordRatio(text, "工具", "失败", "重试", "参数");
        }
        return 0.8;
    }

    private double keywordRatio(String text, String... keywords) {
        int hits = 0;
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                hits++;
            }
        }
        return (double) hits / keywords.length;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String toJson(Map<String, Object> metrics) {
        try {
            return objectMapper.writeValueAsString(metrics);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
