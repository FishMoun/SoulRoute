package com.fishmoun.soulroute.evolution;

public record ReactRunRecord(
        Long id,
        Long userId,
        String chatId,
        String task,
        String answer,
        String state,
        FailurePattern failure
) {
}
