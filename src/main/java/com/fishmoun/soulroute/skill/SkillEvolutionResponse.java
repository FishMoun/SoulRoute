package com.fishmoun.soulroute.skill;

public record SkillEvolutionResponse(
        Long eventId,
        Long skillId,
        Long versionId,
        int versionNo,
        String status,
        Double evaluationScore,
        Boolean activated
) {
}
