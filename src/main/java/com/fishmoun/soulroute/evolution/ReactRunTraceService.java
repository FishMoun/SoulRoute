package com.fishmoun.soulroute.evolution;

import com.fishmoun.soulroute.agent.AgentRunResult;
import com.fishmoun.soulroute.agent.AgentStep;
import com.fishmoun.soulroute.agent.AgentState;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ReactRunTraceService {

    private final JdbcTemplate jdbcTemplate;

    public ReactRunTraceService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ReactRunRecord save(Long userId, String chatId, String task, AgentRunResult result) {
        FailurePattern failure = detectFailure(task, result);
        Long runId = jdbcTemplate.queryForObject("""
                        INSERT INTO react_runs(user_id, chat_id, task, answer, state, failed, failure_type, failure_signal)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                userId,
                chatId,
                task,
                result.answer(),
                result.state().name(),
                failure.failed(),
                failure.type(),
                failure.signal());
        for (AgentStep step : result.steps()) {
            jdbcTemplate.update("""
                            INSERT INTO react_run_steps(run_id, step_no, thought, action, action_input, observation)
                            VALUES (?, ?, ?, ?, ?, ?)
                            """,
                    runId,
                    step.step(),
                    step.thought(),
                    step.action(),
                    step.actionInput(),
                    step.observation());
        }
        return new ReactRunRecord(runId, userId, chatId, task, result.answer(), result.state().name(), failure);
    }

    public void attachSkillVersion(Long runId, Long versionId) {
        if (runId == null || versionId == null) {
            return;
        }
        jdbcTemplate.update("UPDATE react_runs SET created_skill_version_id = ? WHERE id = ?", versionId, runId);
    }

    private FailurePattern detectFailure(String task, AgentRunResult result) {
        if (result == null) {
            return new FailurePattern(true, "AGENT_ERROR", "智能体返回结果为空");
        }
        String answer = safe(result.answer());
        String joinedObservations = result.steps().stream()
                .map(AgentStep::observation)
                .filter(value -> value != null && !value.isBlank())
                .reduce("", (left, right) -> left + "\n" + right);
        String all = (answer + "\n" + joinedObservations).toLowerCase();
        if (result.state() == AgentState.ERROR || all.contains("agent error")) {
            return new FailurePattern(true, "AGENT_ERROR", compact(answer, joinedObservations));
        }
        if (all.contains("error generating pdf") || all.contains("pdf生成失败") || all.contains("glyph") || all.contains("cannot invoke")) {
            return new FailurePattern(true, "PDF_FAILURE", compact(answer, joinedObservations));
        }
        if (all.contains("unknown tool") || all.contains("tool execution failed")) {
            return new FailurePattern(true, "TOOL_FAILURE", compact(answer, joinedObservations));
        }
        if (answer.contains("已达到 ReAct 最大步骤数") || answer.contains("Reached max ReAct steps")) {
            return new FailurePattern(true, "MAX_STEPS", compact(answer, joinedObservations));
        }
        if (looksLikeCorrection(task)) {
            return new FailurePattern(true, "USER_CORRECTION", safe(task));
        }
        if (looksLikeRetrievalMiss(task, answer, joinedObservations)) {
            return new FailurePattern(true, "RETRIEVAL_WEAK", compact(answer, joinedObservations));
        }
        return FailurePattern.none();
    }

    private boolean looksLikeCorrection(String task) {
        String text = safe(task);
        return text.contains("不对") || text.contains("不是") || text.contains("重新")
                || text.contains("纠正") || text.contains("修正") || text.contains("不符合")
                || text.contains("图片完全不符合") || text.contains("检索不准");
    }

    private boolean looksLikeRetrievalMiss(String task, String answer, String observations) {
        String text = (safe(task) + "\n" + safe(answer) + "\n" + safe(observations));
        return text.contains("没有找到") || text.contains("未检索到") || text.contains("知识库为空")
                || text.contains("No relevant") || text.contains("检索结果不足");
    }

    private String compact(String answer, String observations) {
        String text = (safe(answer) + "\n" + safe(observations)).trim();
        return text.length() > 2000 ? text.substring(0, 2000) + "\n...[truncated]" : text;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
