package com.fishmoun.soulroute.tools;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.stream.Collectors;

public class TravelKnowledgeTool {

    private final DocumentRetriever documentRetriever;

    public TravelKnowledgeTool(DocumentRetriever documentRetriever) {
        this.documentRetriever = documentRetriever;
    }

    @Tool(description = "Search local SoulRoute travel knowledge base with hybrid vector and metadata retrieval")
    public String travelKnowledgeSearch(@ToolParam(description = "Travel question or destination keyword") String query) {
        List<Document> documents = documentRetriever.retrieve(new Query(query));
        if (documents.isEmpty()) {
            return "No matching travel knowledge chunks found.";
        }
        return documents.stream()
                .map(document -> """
                        id: %s
                        score: %s
                        metadata: %s
                        content:
                        %s
                        """.formatted(
                        document.getId(),
                        document.getScore(),
                        document.getMetadata(),
                        truncate(document.getText(), 1800)
                ))
                .collect(Collectors.joining("\n---\n"));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "\n...[truncated]";
    }
}
