package com.fishmoun.soulroute.evolution;

public record FailurePattern(
        boolean failed,
        String type,
        String signal
) {

    public static FailurePattern none() {
        return new FailurePattern(false, null, null);
    }
}
