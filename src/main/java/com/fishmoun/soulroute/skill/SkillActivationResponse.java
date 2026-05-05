package com.fishmoun.soulroute.skill;

public record SkillActivationResponse(
        Long skillId,
        Long previousVersionId,
        Long activeVersionId,
        String status
) {
}
