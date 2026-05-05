package com.fishmoun.soulroute.skill;

public record SkillEvolutionRequest(
        String skillKey,
        String source,
        String signal,
        String proposal
) {
}
