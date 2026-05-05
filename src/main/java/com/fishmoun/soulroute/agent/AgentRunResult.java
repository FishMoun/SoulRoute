package com.fishmoun.soulroute.agent;

import java.util.List;

public record AgentRunResult(
        String answer,
        AgentState state,
        List<AgentStep> steps
) {
}
