package com.fishmoun.soulroute.conversation;

import java.time.Instant;
import java.util.List;

public record ConversationDetail(
        String chatId,
        String title,
        Instant createdAt,
        Instant updatedAt,
        List<ConversationMessage> messages
) {
}
