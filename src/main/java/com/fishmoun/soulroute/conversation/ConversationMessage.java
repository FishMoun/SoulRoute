package com.fishmoun.soulroute.conversation;

import java.time.Instant;

public record ConversationMessage(
        String role,
        String content,
        String meta,
        Instant createdAt
) {
}
