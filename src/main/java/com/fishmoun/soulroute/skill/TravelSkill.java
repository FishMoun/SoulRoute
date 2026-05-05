package com.fishmoun.soulroute.skill;

public record TravelSkill(
        Long id,
        String skillKey,
        String name,
        String description,
        Long activeVersionId,
        String activePrompt
) {
}
