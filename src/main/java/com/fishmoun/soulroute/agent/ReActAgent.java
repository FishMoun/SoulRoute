package com.fishmoun.soulroute.agent;

public abstract class ReActAgent extends BaseAgent {

    protected ReActAgent(int maxSteps) {
        super(maxSteps);
    }

    @Override
    protected ReActStepResult step(AgentContext context, int stepNumber) {
        ReActDecision decision = think(context, stepNumber);
        if (decision.finalAnswer() != null && !decision.finalAnswer().isBlank()) {
            return ReActStepResult.finished(decision.thought(), decision.finalAnswer());
        }
        String observation = act(decision);
        return ReActStepResult.observed(
                decision.thought(),
                decision.action(),
                decision.actionInput(),
                observation
        );
    }

    protected abstract ReActDecision think(AgentContext context, int stepNumber);

    protected abstract String act(ReActDecision decision);
}
