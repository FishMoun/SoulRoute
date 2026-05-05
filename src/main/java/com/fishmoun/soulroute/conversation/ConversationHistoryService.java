package com.fishmoun.soulroute.conversation;

import com.fishmoun.soulroute.chatmemory.FileBasedChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
public class ConversationHistoryService {

    private static final int MAX_CONTEXT_MESSAGES = 12;
    private static final String MEMORY_FILE_SUFFIX = ".kryo";

    private final Path baseDir;
    private final FileBasedChatMemory chatMemory;
    private final JdbcTemplate jdbcTemplate;

    public ConversationHistoryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.baseDir = Path.of(System.getProperty("user.dir"), "chat-memory");
        this.chatMemory = new FileBasedChatMemory(baseDir.toString());
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create chat memory directory", e);
        }
    }

    public List<ConversationSummary> listConversations() {
        try (var paths = Files.list(baseDir)) {
            return paths
                    .filter(path -> path.getFileName().toString().endsWith(MEMORY_FILE_SUFFIX))
                    .map(this::toSummary)
                    .sorted(Comparator.comparing(ConversationSummary::updatedAt).reversed())
                    .toList();
        } catch (IOException e) {
            log.warn("Failed to list conversations", e);
            return List.of();
        }
    }

    public List<ConversationSummary> listConversations(Long userId) {
        if (userId == null) {
            return listConversations();
        }
        return jdbcTemplate.query("""
                        SELECT c.chat_id, c.title, c.created_at, c.updated_at, COUNT(m.id) AS message_count
                        FROM conversations c
                        LEFT JOIN conversation_messages m ON m.conversation_id = c.id
                        WHERE c.user_id = ?
                        GROUP BY c.id
                        ORDER BY c.updated_at DESC
                        """,
                (rs, rowNum) -> new ConversationSummary(
                        rs.getString("chat_id"),
                        rs.getString("title"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant(),
                        rs.getInt("message_count")
                ),
                userId);
    }

    public ConversationDetail getConversation(String chatId) {
        String conversationId = normalizeChatId(chatId);
        List<Message> messages = chatMemory.get(conversationId);
        ConversationTimestamps timestamps = timestamps(fileFor(conversationId));
        return new ConversationDetail(
                conversationId,
                buildTitle(messages),
                timestamps.createdAt(),
                timestamps.updatedAt(),
                toConversationMessages(messages, timestamps.updatedAt())
        );
    }

    public ConversationDetail getConversation(Long userId, String chatId) {
        if (userId == null) {
            return getConversation(chatId);
        }
        String conversationId = normalizeChatId(chatId);
        Long id = findConversationId(userId, conversationId);
        if (id == null) {
            Instant now = Instant.now();
            return new ConversationDetail(conversationId, "新的旅行会话", now, now, List.of());
        }
        ConversationSummary summary = jdbcTemplate.query("""
                        SELECT c.chat_id, c.title, c.created_at, c.updated_at, COUNT(m.id) AS message_count
                        FROM conversations c
                        LEFT JOIN conversation_messages m ON m.conversation_id = c.id
                        WHERE c.id = ?
                        GROUP BY c.id
                        """,
                rs -> rs.next()
                        ? new ConversationSummary(
                        rs.getString("chat_id"),
                        rs.getString("title"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant(),
                        rs.getInt("message_count"))
                        : null,
                id);
        List<ConversationMessage> messages = jdbcTemplate.query("""
                        SELECT role, content, meta, created_at
                        FROM conversation_messages
                        WHERE conversation_id = ?
                        ORDER BY created_at, id
                        """,
                (rs, rowNum) -> new ConversationMessage(
                        rs.getString("role"),
                        rs.getString("content"),
                        rs.getString("meta"),
                        rs.getTimestamp("created_at").toInstant()),
                id);
        Instant now = Instant.now();
        return new ConversationDetail(
                conversationId,
                summary == null ? "新的旅行会话" : summary.title(),
                summary == null ? now : summary.createdAt(),
                summary == null ? now : summary.updatedAt(),
                messages
        );
    }

    public void appendMessage(String chatId, String role, String content, String meta) {
        String conversationId = normalizeChatId(chatId);
        Message message = "assistant".equals(role)
                ? new AssistantMessage(content)
                : new UserMessage(content);
        chatMemory.add(conversationId, message);
    }

    public void appendMessage(Long userId, String chatId, String role, String content, String meta) {
        if (userId == null) {
            appendMessage(chatId, role, content, meta);
            return;
        }
        String conversationId = normalizeChatId(chatId);
        Long id = ensureConversation(userId, conversationId, content);
        jdbcTemplate.update("""
                        INSERT INTO conversation_messages(conversation_id, role, content, meta)
                        VALUES (?, ?, ?, ?)
                        """, id, role, content, meta);
        if ("user".equals(role)) {
            jdbcTemplate.update("""
                            UPDATE conversations
                            SET title = CASE WHEN title = '新的旅行会话' THEN ? ELSE title END,
                                updated_at = now()
                            WHERE id = ?
                            """, buildTitle(content), id);
        } else {
            jdbcTemplate.update("UPDATE conversations SET updated_at = now() WHERE id = ?", id);
        }
    }

    public void clearConversation(String chatId) {
        chatMemory.clear(normalizeChatId(chatId));
    }

    public void clearConversation(Long userId, String chatId) {
        if (userId == null) {
            clearConversation(chatId);
            return;
        }
        jdbcTemplate.update("DELETE FROM conversations WHERE user_id = ? AND chat_id = ?", userId, normalizeChatId(chatId));
    }

    public String buildContext(String chatId) {
        List<Message> messages = chatMemory.get(normalizeChatId(chatId));
        if (messages.isEmpty()) {
            return "";
        }
        int fromIndex = Math.max(0, messages.size() - MAX_CONTEXT_MESSAGES);
        StringBuilder context = new StringBuilder("以下是当前会话的历史记录，请结合上下文回答最新问题：\n");
        for (Message message : messages.subList(fromIndex, messages.size())) {
            String role = message.getMessageType() == MessageType.USER ? "用户" : "助手";
            context.append(role).append(": ").append(message.getText()).append("\n");
        }
        return context.toString();
    }

    public String buildContext(Long userId, String chatId) {
        if (userId == null) {
            return buildContext(chatId);
        }
        Long id = findConversationId(userId, normalizeChatId(chatId));
        if (id == null) {
            return "";
        }
        List<ConversationMessage> messages = jdbcTemplate.query("""
                        SELECT role, content, meta, created_at
                        FROM conversation_messages
                        WHERE conversation_id = ?
                        ORDER BY created_at DESC, id DESC
                        LIMIT ?
                        """,
                (rs, rowNum) -> new ConversationMessage(
                        rs.getString("role"),
                        rs.getString("content"),
                        rs.getString("meta"),
                        rs.getTimestamp("created_at").toInstant()),
                id,
                MAX_CONTEXT_MESSAGES);
        if (messages.isEmpty()) {
            return "";
        }
        StringBuilder context = new StringBuilder("以下是当前会话的历史记录，请结合上下文回答最新问题：\n");
        for (int i = messages.size() - 1; i >= 0; i--) {
            ConversationMessage message = messages.get(i);
            String role = "user".equals(message.role()) ? "用户" : "助手";
            context.append(role).append(": ").append(message.content()).append("\n");
        }
        return context.toString();
    }

    private Long ensureConversation(Long userId, String chatId, String titleSeed) {
        Long existing = findConversationId(userId, chatId);
        if (existing != null) {
            return existing;
        }
        return jdbcTemplate.queryForObject("""
                        INSERT INTO conversations(user_id, chat_id, title)
                        VALUES (?, ?, ?)
                        ON CONFLICT (user_id, chat_id) DO UPDATE SET updated_at = conversations.updated_at
                        RETURNING id
                        """, Long.class, userId, chatId, buildTitle(titleSeed));
    }

    private Long findConversationId(Long userId, String chatId) {
        return jdbcTemplate.query("""
                        SELECT id FROM conversations WHERE user_id = ? AND chat_id = ?
                        """,
                rs -> rs.next() ? rs.getLong("id") : null,
                userId,
                normalizeChatId(chatId));
    }

    private ConversationSummary toSummary(Path path) {
        String fileName = path.getFileName().toString();
        String chatId = fileName.substring(0, fileName.length() - MEMORY_FILE_SUFFIX.length());
        List<Message> messages = chatMemory.get(chatId);
        ConversationTimestamps timestamps = timestamps(path);
        return new ConversationSummary(
                chatId,
                buildTitle(messages),
                timestamps.createdAt(),
                timestamps.updatedAt(),
                messages.size()
        );
    }

    private Path fileFor(String chatId) {
        return baseDir.resolve(normalizeChatId(chatId) + MEMORY_FILE_SUFFIX);
    }

    private String normalizeChatId(String chatId) {
        if (!StringUtils.hasText(chatId)) {
            return "soulroute-" + System.currentTimeMillis();
        }
        return chatId.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private List<ConversationMessage> toConversationMessages(List<Message> messages, Instant fallbackCreatedAt) {
        return messages.stream()
                .map(message -> new ConversationMessage(
                        toRole(message),
                        message.getText(),
                        "Kryo",
                        fallbackCreatedAt
                ))
                .toList();
    }

    private String toRole(Message message) {
        if (message.getMessageType() == MessageType.ASSISTANT) {
            return "assistant";
        }
        if (message.getMessageType() == MessageType.USER) {
            return "user";
        }
        return message.getMessageType().getValue();
    }

    private String buildTitle(List<Message> messages) {
        return messages.stream()
                .filter(message -> message.getMessageType() == MessageType.USER)
                .findFirst()
                .map(Message::getText)
                .map(this::buildTitle)
                .orElse("新的旅行会话");
    }

    private String buildTitle(String content) {
        String title = content == null ? "" : content.replaceAll("\\s+", " ").trim();
        if (title.length() > 28) {
            return title.substring(0, 28) + "...";
        }
        return StringUtils.hasText(title) ? title : "新的旅行会话";
    }

    private ConversationTimestamps timestamps(Path path) {
        Instant now = Instant.now();
        if (!Files.exists(path)) {
            return new ConversationTimestamps(now, now);
        }
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            return new ConversationTimestamps(toInstant(attrs.creationTime(), now), toInstant(attrs.lastModifiedTime(), now));
        } catch (IOException e) {
            log.warn("Failed to read conversation timestamps: {}", path, e);
            return new ConversationTimestamps(now, now);
        }
    }

    private Instant toInstant(FileTime fileTime, Instant fallback) {
        return fileTime == null ? fallback : fileTime.toInstant();
    }

    private record ConversationTimestamps(Instant createdAt, Instant updatedAt) {
    }
}
