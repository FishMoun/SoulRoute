package com.fishmoun.soulroute.skill;

import java.time.Instant;

public record TravelSkillVersion(
        Long id,
        Long skillId,
        int versionNo,
        String prompt,
        String status,
        String source,
        String metricsJson,
        Instant createdAt
) {
}
