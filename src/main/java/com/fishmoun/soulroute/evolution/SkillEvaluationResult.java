package com.fishmoun.soulroute.evolution;

public record SkillEvaluationResult(
        double score,
        boolean passed,
        String metricsJson
) {
}
