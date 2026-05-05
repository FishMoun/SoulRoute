package com.fishmoun.soulroute.rag;

import java.util.List;

public record QueryUnderstanding(
        String rewrittenQuery,
        List<String> destinations,
        List<String> expandedDestinations,
        Integer days,
        String budget,
        String pace,
        String companion,
        List<String> interests,
        String outputFormat
) {

    public static QueryUnderstanding empty() {
        return new QueryUnderstanding(null, List.of(), List.of(), null, null, null, null, List.of(), null);
    }

    public boolean hasSignals() {
        return hasText(rewrittenQuery)
                || !destinations.isEmpty()
                || !expandedDestinations.isEmpty()
                || days != null
                || hasText(budget)
                || hasText(pace)
                || hasText(companion)
                || !interests.isEmpty()
                || hasText(outputFormat);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
