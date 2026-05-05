package com.fishmoun.soulroute.agent;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.function.Consumer;

@Slf4j
public abstract class BaseAgent {

    private final int maxSteps;
    private AgentState state = AgentState.IDLE;

    protected BaseAgent(int maxSteps) {
        this.maxSteps = maxSteps;
    }

    public AgentRunResult run(String task, String chatId) {
        return run(task, chatId, null);
    }

    public AgentRunResult run(String task, String chatId, Consumer<AgentStep> stepListener) {
        AgentContext context = new AgentContext(task, chatId);
        return run(context, stepListener);
    }

    public AgentRunResult run(String task,
                              String chatId,
                              Long userId,
                              com.fishmoun.soulroute.preference.TravelPreference preference,
                              java.util.List<com.fishmoun.soulroute.skill.TravelSkill> skills,
                              Consumer<AgentStep> stepListener) {
        AgentContext context = new AgentContext(task, chatId, userId, preference, skills);
        return run(context, stepListener);
    }

    AgentRunResult run(AgentContext context, Consumer<AgentStep> stepListener) {
        AgentState runState = AgentState.RUNNING;
        state = runState;
        String answer = null;
        try {
            for (int i = 1; i <= maxSteps && runState == AgentState.RUNNING; i++) {
                ReActStepResult stepResult = step(context, i);
                AgentStep agentStep = new AgentStep(
                        i,
                        stepResult.thought(),
                        stepResult.action(),
                        stepResult.actionInput(),
                        stepResult.observation()
                );
                context.addStep(agentStep);
                if (stepListener != null) {
                    stepListener.accept(agentStep);
                }
                if (stepResult.finished()) {
                    answer = stepResult.finalAnswer();
                    runState = AgentState.FINISHED;
                    state = runState;
                }
            }
            if (runState == AgentState.RUNNING) {
                runState = AgentState.FINISHED;
                state = runState;
                answer = fallbackAnswer(context);
            }
            return new AgentRunResult(answer, runState, new ArrayList<>(context.steps()));
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("Agent run failed", e);
            return new AgentRunResult("Agent error: " + e.getMessage(), AgentState.ERROR, new ArrayList<>(context.steps()));
        }
    }

    protected abstract ReActStepResult step(AgentContext context, int stepNumber);

    private String fallbackAnswer(AgentContext context) {
        return context.steps().stream()
                .map(AgentStep::observation)
                .filter(observation -> observation != null && !observation.isBlank())
                .reduce((previous, current) -> current)
                .map(observation -> {
                    if (observation.startsWith("PDF generated successfully to: ")) {
                        return "PDF 已生成，保存路径：" + observation.substring("PDF generated successfully to: ".length());
                    }
                    return "已达到 ReAct 最大步骤数。最后一次工具执行结果：\n" + observation;
                })
                .orElse("已达到 ReAct 最大步骤数，请简化任务或提高 soulroute.agent.max-steps 配置。");
    }

    protected AgentState state() {
        return state;
    }
}
