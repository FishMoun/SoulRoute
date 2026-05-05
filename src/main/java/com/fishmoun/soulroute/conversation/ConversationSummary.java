package com.fishmoun.soulroute.conversation;

import java.time.Instant;

public record ConversationSummary(
        String chatId,
        String title,
        Instant createdAt,
        Instant updatedAt,
        int messageCount
) {
}
