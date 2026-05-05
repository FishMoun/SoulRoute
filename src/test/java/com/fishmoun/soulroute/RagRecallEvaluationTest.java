package com.fishmoun.soulroute;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SpringBootTest(properties = {
        "spring.ai.mcp.client.enabled=false",
        "soulroute.rag.index-on-startup=false"
})
class RagRecallEvaluationTest {

    private static final int TOP_K = 5;
    private static final int LOCATION_LIMIT = 40;
    private static final List<String> QUERY_PATTERNS = List.of(
            "%s旅行攻略",
            "%s三日游路线",
            "%s景点推荐",
            "%s美食推荐",
            "%s住宿交通避坑"
    );

    private final VectorStore vectorStore;
    private final DocumentRetriever hybridRetriever;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    RagRecallEvaluationTest(@Qualifier("travelAppVectorStore") VectorStore vectorStore,
                            DocumentRetriever hybridRetriever,
                            JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.hybridRetriever = hybridRetriever;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void evaluateVectorOnlyVsHybridRecall() throws Exception {
        List<String> locations = loadTopLocations();
        List<EvalCase> cases = buildCases(locations);

        EvalSummary vectorOnly = evaluate("vector-only", cases, query -> vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(TOP_K)
                        .similarityThreshold(0.65)
                        .build()
        ));
        EvalSummary hybrid = evaluate("hybrid", cases, query -> hybridRetriever.retrieve(new Query(query)));

        String report = buildReport(locations, cases.size(), vectorOnly, hybrid);
        Path output = Path.of("target", "rag-recall-evaluation.md");
        Files.createDirectories(output.getParent());
        Files.writeString(output, report);
        System.out.println(report);
    }

    private List<String> loadTopLocations() {
        return jdbcTemplate.queryForList("""
                SELECT location
                FROM (
                    SELECT jsonb_array_elements_text(metadata->'location') AS location, count(*) AS chunk_count
                    FROM travel_document_chunks
                    WHERE jsonb_typeof(metadata->'location') = 'array'
                    GROUP BY 1
                ) t
                WHERE location <> ''
                ORDER BY chunk_count DESC, location
                LIMIT ?
                """, String.class, LOCATION_LIMIT);
    }

    private List<EvalCase> buildCases(List<String> locations) {
        List<EvalCase> cases = new ArrayList<>();
        for (String location : locations) {
            for (String pattern : QUERY_PATTERNS) {
                cases.add(new EvalCase(String.format(pattern, location), location));
            }
        }
        return cases;
    }

    private EvalSummary evaluate(String name, List<EvalCase> cases, Retrieval retrieval) {
        List<EvalRow> rows = new ArrayList<>();
        int hits = 0;
        int totalRelevant = 0;
        double precisionSum = 0.0;
        double normalizedRecallSum = 0.0;
        double reciprocalRankSum = 0.0;

        for (EvalCase evalCase : cases) {
            List<Document> documents = retrieval.retrieve(evalCase.query()).stream()
                    .limit(TOP_K)
                    .toList();
            int relevantInTopK = 0;
            int firstRelevantRank = 0;
            for (int i = 0; i < documents.size(); i++) {
                if (isRelevant(documents.get(i), evalCase.expectedLocation())) {
                    relevantInTopK++;
                    if (firstRelevantRank == 0) {
                        firstRelevantRank = i + 1;
                    }
                }
            }

            boolean hit = relevantInTopK > 0;
            if (hit) {
                hits++;
                reciprocalRankSum += 1.0 / firstRelevantRank;
            }
            totalRelevant += relevantInTopK;
            precisionSum += relevantInTopK / (double) TOP_K;
            normalizedRecallSum += relevantInTopK / (double) TOP_K;
            rows.add(new EvalRow(evalCase.query(), evalCase.expectedLocation(), hit, relevantInTopK,
                    firstRelevantRank, summarizeResults(documents)));
        }

        return new EvalSummary(name, cases.size(), hits, totalRelevant,
                hits / (double) cases.size(),
                precisionSum / cases.size(),
                normalizedRecallSum / cases.size(),
                reciprocalRankSum / cases.size(),
                rows);
    }

    private boolean isRelevant(Document document, String expectedLocation) {
        Map<String, Object> metadata = document.getMetadata();
        if (contains(metadata.get("location"), expectedLocation)) {
            return true;
        }
        Object filename = metadata.get("filename");
        return filename != null && String.valueOf(filename).contains(expectedLocation);
    }

    private boolean contains(Object value, String expected) {
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (expected.equals(String.valueOf(item))) {
                    return true;
                }
            }
            return false;
        }
        return expected.equals(String.valueOf(value));
    }

    private List<String> summarizeResults(List<Document> documents) {
        List<String> result = new ArrayList<>();
        for (Document document : documents) {
            Object filename = document.getMetadata().get("filename");
            Object location = document.getMetadata().get("location");
            Object strategy = document.getMetadata().get("retrieval_strategy");
            result.add("%s | %s | %s".formatted(filename, location, strategy));
        }
        return result;
    }

    private String buildReport(List<String> locations, int caseCount, EvalSummary vectorOnly, EvalSummary hybrid) {
        StringBuilder report = new StringBuilder();
        report.append("# RAG Recall Evaluation\n\n");
        report.append("- 数据表：`travel_document_chunks`\n");
        report.append("- 评估位置数：").append(locations.size()).append("\n");
        report.append("- 查询模板数：").append(QUERY_PATTERNS.size()).append("\n");
        report.append("- 总查询数：").append(caseCount).append("\n");
        report.append("- TopK：").append(TOP_K).append("\n");
        report.append("- 相关性判定：返回 chunk 的 `metadata.location` 包含目标地点，或 `filename` 包含目标地点。\n");
        report.append("- Recall@5：查询级命中率，只要 Top5 至少 1 条相关就算命中。\n");
        report.append("- Precision@5：Top5 中相关结果占比的平均值。\n");
        report.append("- NormalizedRelevant@5：Top5 中相关条数 / 5 的平均值。\n");
        report.append("- MRR@5：第一条相关结果倒数排名的平均值。\n\n");

        report.append("| strategy | queries | hits | Recall@5 | Precision@5 | NormalizedRelevant@5 | MRR@5 | relevant chunks in Top5 |\n");
        report.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        appendSummaryRow(report, vectorOnly);
        appendSummaryRow(report, hybrid);

        report.append("\n## Delta\n\n");
        report.append("- Recall@5 提升：").append(formatPercent(hybrid.recallAtK() - vectorOnly.recallAtK())).append("\n");
        report.append("- Precision@5 提升：").append(formatPercent(hybrid.precisionAtK() - vectorOnly.precisionAtK())).append("\n");
        report.append("- MRR@5 提升：").append(formatDecimal(hybrid.mrrAtK() - vectorOnly.mrrAtK())).append("\n\n");

        appendMisses(report, vectorOnly, "Vector-only misses");
        appendMisses(report, hybrid, "Hybrid misses");
        return report.toString();
    }

    private void appendSummaryRow(StringBuilder report, EvalSummary summary) {
        report.append("| ")
                .append(summary.name())
                .append(" | ")
                .append(summary.queries())
                .append(" | ")
                .append(summary.hits())
                .append(" | ")
                .append(formatPercent(summary.recallAtK()))
                .append(" | ")
                .append(formatPercent(summary.precisionAtK()))
                .append(" | ")
                .append(formatPercent(summary.normalizedRecallAtK()))
                .append(" | ")
                .append(formatDecimal(summary.mrrAtK()))
                .append(" | ")
                .append(summary.totalRelevantInTopK())
                .append(" |\n");
    }

    private void appendMisses(StringBuilder report, EvalSummary summary, String title) {
        List<EvalRow> misses = summary.rows().stream()
                .filter(row -> !row.hit())
                .limit(20)
                .toList();
        report.append("## ").append(title).append("\n\n");
        if (misses.isEmpty()) {
            report.append("- 无未命中查询。\n\n");
            return;
        }
        for (EvalRow row : misses) {
            report.append("- `").append(row.query()).append("` expected `")
                    .append(row.expectedLocation()).append("`, top result: ")
                    .append(row.results().isEmpty() ? "empty" : row.results().get(0))
                    .append("\n");
        }
        report.append("\n");
    }

    private String formatPercent(double value) {
        return formatDecimal(value * 100) + "%";
    }

    private String formatDecimal(double value) {
        return BigDecimal.valueOf(value)
                .setScale(4, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    private record EvalCase(String query, String expectedLocation) {
    }

    private record EvalRow(String query, String expectedLocation, boolean hit, int relevantInTopK,
                           int firstRelevantRank, List<String> results) {
    }

    private record EvalSummary(String name, int queries, int hits, int totalRelevantInTopK,
                               double recallAtK, double precisionAtK, double normalizedRecallAtK,
                               double mrrAtK, List<EvalRow> rows) {
    }

    private interface Retrieval {
        List<Document> retrieve(String query);
    }
}
