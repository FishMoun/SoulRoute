package com.fishmoun.soulroute.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/travel/rag")
public class RagDebugController {

    private final JdbcTemplate jdbcTemplate;
    private final DocumentRetriever documentRetriever;
    private final ObjectMapper objectMapper;
    private final String qualifiedTableName;

    public RagDebugController(JdbcTemplate jdbcTemplate,
                              DocumentRetriever documentRetriever,
                              ObjectMapper objectMapper,
                              @Value("${soulroute.rag.vector.schema-name:public}") String schemaName,
                              @Value("${soulroute.rag.vector.table-name:travel_document_chunks}") String tableName) {
        this.jdbcTemplate = jdbcTemplate;
        this.documentRetriever = documentRetriever;
        this.objectMapper = objectMapper;
        this.qualifiedTableName = quoteIdentifier(schemaName) + "." + quoteIdentifier(tableName);
    }

    @GetMapping("/debug")
    public ResponseEntity<?> debug(@RequestParam(defaultValue = "上海") String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return ResponseEntity.badRequest().body(Map.of("error", "keyword 不能为空"));
        }

        String likePattern = "%" + keyword.trim() + "%";
        Integer contentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + qualifiedTableName + " WHERE content ILIKE ?",
                Integer.class,
                likePattern
        );

        Integer metadataCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + qualifiedTableName + " WHERE metadata @> ?::jsonb",
                Integer.class,
                jsonbArrayContains("location", keyword.trim())
        );

        List<Map<String, Object>> samples = jdbcTemplate.query(
                "SELECT id, left(content, 200) AS snippet, metadata::text AS metadata FROM " + qualifiedTableName
                        + " WHERE content ILIKE ? LIMIT 3",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("snippet", rs.getString("snippet"));
                    row.put("metadata", rs.getString("metadata"));
                    return row;
                },
                likePattern
        );

        List<Map<String, Object>> retrieved = documentRetriever.retrieve(new Query(keyword)).stream()
                .map(doc -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", doc.getId());
                    row.put("score", doc.getScore());
                    row.put("metadata", doc.getMetadata());
                    row.put("snippet", truncate(doc.getText(), 200));
                    return row;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("keyword", keyword.trim());
        result.put("contentCount", contentCount);
        result.put("metadataLocationCount", metadataCount);
        result.put("sampleRows", samples);
        result.put("retrieved", retrieved);
        return ResponseEntity.ok(result);
    }

    private String jsonbArrayContains(String metadataKey, String value) {
        try {
            return objectMapper.writeValueAsString(Map.of(metadataKey, List.of(value)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("构建 jsonb 查询条件失败", e);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private String quoteIdentifier(String identifier) {
        if (!identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("非法 PostgreSQL identifier: " + identifier);
        }
        return "\"" + identifier + "\"";
    }
}