package com.fishmoun.soulroute.preference;

import java.time.Instant;

public record TravelPreference(
        String departureCity,
        String budgetLevel,
        String travelPace,
        String companionType,
        String interests,
        String dietaryRestrictions,
        String accommodationPreference,
        String extraNotes,
        Instant updatedAt
) {

    public static TravelPreference empty() {
        return new TravelPreference(null, null, null, null, null, null, null, null, null);
    }

    public boolean hasAnyPreference() {
        return hasText(departureCity)
                || hasText(budgetLevel)
                || hasText(travelPace)
                || hasText(companionType)
                || hasText(interests)
                || hasText(dietaryRestrictions)
                || hasText(accommodationPreference)
                || hasText(extraNotes);
    }

    public String toPromptText() {
        StringBuilder builder = new StringBuilder();
        append(builder, "常用出发地", departureCity);
        append(builder, "预算偏好", budgetLevel);
        append(builder, "行程节奏", travelPace);
        append(builder, "同行类型", companionType);
        append(builder, "兴趣偏好", interests);
        append(builder, "饮食限制", dietaryRestrictions);
        append(builder, "住宿偏好", accommodationPreference);
        append(builder, "其他备注", extraNotes);
        return builder.toString().trim();
    }

    private void append(StringBuilder builder, String label, String value) {
        if (value != null && !value.isBlank()) {
            builder.append("- ").append(label).append(": ").append(value.trim()).append("\n");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
