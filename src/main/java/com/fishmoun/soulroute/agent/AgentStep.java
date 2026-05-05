package com.fishmoun.soulroute.agent;

public record AgentStep(
        int step,
        String thought,
        String action,
        String actionInput,
        String observation
) {
}
