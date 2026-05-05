package com.fishmoun.soulroute.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
public class HybridTravelDocumentRetriever implements DocumentRetriever {

    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
    };
    private static final List<String> LOCATION_TERMS = List.of(
            "北京", "天津", "上海", "重庆", "河北", "山西", "内蒙古", "辽宁", "吉林", "黑龙江", "江苏", "浙江", "安徽", "福建", "江西", "山东",
            "河南", "湖北", "湖南", "广东", "广西", "海南", "四川", "贵州", "云南", "西藏", "陕西", "甘肃", "青海", "宁夏", "新疆",
            "香港", "澳门", "台湾", "哈尔滨", "长春", "沈阳", "大连", "丹东", "延吉", "大庆", "齐齐哈尔", "牡丹江", "锦州", "营口", "通化", "本溪",
            "南京", "苏州", "杭州", "宁波", "绍兴", "嘉兴", "湖州", "温州", "舟山", "台州", "金华", "丽水", "合肥", "黄山", "芜湖", "安庆",
            "福州", "厦门", "泉州", "漳州", "龙岩", "宁德", "南昌", "九江", "景德镇", "赣州", "济南", "青岛", "烟台", "威海", "淄博", "泰安",
            "武汉", "宜昌", "长沙", "岳阳", "郑州", "洛阳", "开封", "安阳", "南阳", "广州", "深圳", "珠海", "佛山", "东莞", "中山", "惠州",
            "南宁", "桂林", "柳州", "北海", "三亚", "海口", "成都", "昆明", "大理", "丽江", "贵阳", "遵义", "拉萨", "日喀则",
            "西安", "兰州", "西宁", "银川", "乌鲁木齐", "喀什", "伊犁", "敦煌", "呼和浩特", "呼伦贝尔", "鄂尔多斯",
            "泰国", "曼谷", "越南", "韩国", "土耳其", "奥地利", "捷克", "匈牙利", "乌兹别克", "卡达", "多哈", "阿拉伯"
    );
    private static final Map<String, List<String>> TAG_KEYWORDS = new LinkedHashMap<>();

    static {
        TAG_KEYWORDS.put("景点", List.of("景点", "景区", "公园", "古城", "古镇", "博物馆", "寺", "山", "湖", "海", "岛", "草原", "雪山", "峡谷"));
        TAG_KEYWORDS.put("美食", List.of("美食", "小吃", "餐厅", "餐饮", "早餐", "午餐", "晚餐", "夜市", "海鲜", "火锅", "烧烤", "甜品", "咖啡"));
        TAG_KEYWORDS.put("住宿", List.of("住宿", "酒店", "民宿", "客栈", "入住", "宾馆"));
        TAG_KEYWORDS.put("交通", List.of("交通", "地铁", "公交", "机场", "高铁", "火车", "自驾", "打车", "轮渡", "租车"));
        TAG_KEYWORDS.put("路线", List.of("路线", "行程", "Day", "DAY", "第1天", "第一天", "一日游", "两日", "三日", "五日"));
        TAG_KEYWORDS.put("人文", List.of("人文", "历史", "文化", "民俗", "非遗", "古韵", "遗址", "名人", "宗教"));
        TAG_KEYWORDS.put("购物", List.of("购物", "商圈", "步行街", "特产", "纪念品", "市场"));
        TAG_KEYWORDS.put("亲子", List.of("亲子", "儿童", "乐园", "研学"));
        TAG_KEYWORDS.put("避坑", List.of("避坑", "注意", "提醒", "建议", "门票", "预约", "开放时间", "预算", "费用"));
    }

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String qualifiedTableName;
    private final int topK;
    private final int vectorTopK;
    private final int metadataTopK;
    private final double similarityThreshold;
    private final double vectorWeight;
    private final double metadataWeight;

    public HybridTravelDocumentRetriever(VectorStore vectorStore,
                                         JdbcTemplate jdbcTemplate,
                                         ObjectMapper objectMapper,
                                         String schemaName,
                                         String tableName,
                                         int topK,
                                         int vectorTopK,
                                         int metadataTopK,
                                         double similarityThreshold,
                                         double vectorWeight,
                                         double metadataWeight) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.qualifiedTableName = quoteIdentifier(schemaName) + "." + quoteIdentifier(tableName);
        this.topK = topK;
        this.vectorTopK = vectorTopK;
        this.metadataTopK = metadataTopK;
        this.similarityThreshold = similarityThreshold;
        this.vectorWeight = vectorWeight;
        this.metadataWeight = metadataWeight;
    }

    @Override
    public List<Document> retrieve(Query query) {
        String queryText = query.text();
        TravelQuerySignals signals = TravelQuerySignals.from(queryText);
        Map<String, CandidateDocument> candidates = new LinkedHashMap<>();

        for (Document document : retrieveByVector(queryText)) {
            double vectorScore = document.getScore() == null ? 0.7 : document.getScore();
            merge(candidates, document, vectorScore * vectorWeight, "vector");
        }

        for (Document document : retrieveByMetadataAndKeyword(signals)) {
            double metadataScore = calculateMetadataScore(document, signals);
            merge(candidates, document, metadataScore * metadataWeight, "metadata");
        }

        List<Document> documents = candidates.values().stream()
                .sorted(Comparator.comparingDouble(CandidateDocument::score).reversed())
                .limit(topK)
                .map(CandidateDocument::toDocument)
                .toList();
        log.info("混合检索完成，query={}, location={}, tag={}, 返回 chunk 数={}",
                queryText, signals.locations(), signals.tags(), documents.size());
        return documents;
    }

    private List<Document> retrieveByVector(String queryText) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(queryText)
                .topK(vectorTopK)
                .similarityThreshold(similarityThreshold)
                .build();
        return vectorStore.similaritySearch(searchRequest);
    }

    private List<Document> retrieveByMetadataAndKeyword(TravelQuerySignals signals) {
        if (signals.isEmpty()) {
            return List.of();
        }
        List<String> whereParts = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        addJsonbContainment(whereParts, parameters, "location", signals.locations());
        addJsonbContainment(whereParts, parameters, "tag", signals.tags());
        for (String keyword : signals.keywords()) {
            whereParts.add("(content ILIKE ? OR metadata->>'filename' ILIKE ?)");
            String keywordPattern = "%" + keyword + "%";
            parameters.add(keywordPattern);
            parameters.add(keywordPattern);
        }
        parameters.add(metadataTopK);
        String sql = """
                SELECT id, content, metadata::text
                FROM %s
                WHERE %s
                LIMIT ?
                """.formatted(qualifiedTableName, String.join(" OR ", whereParts));
        return jdbcTemplate.query(sql, (rs, rowNum) -> Document.builder()
                .id(rs.getString("id"))
                .text(rs.getString("content"))
                .metadata(readMetadata(rs.getString("metadata")))
                .build(), parameters.toArray());
    }

    private void addJsonbContainment(List<String> whereParts, List<Object> parameters, String metadataKey, Collection<String> values) {
        for (String value : values) {
            whereParts.add("metadata @> ?::jsonb");
            parameters.add(jsonbArrayContains(metadataKey, value));
        }
    }

    private String jsonbArrayContains(String metadataKey, String value) {
        try {
            return objectMapper.writeValueAsString(Map.of(metadataKey, List.of(value)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("构建 jsonb 查询条件失败", e);
        }
    }

    private Map<String, Object> readMetadata(String metadataJson) {
        if (!StringUtils.hasText(metadataJson)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(metadataJson, METADATA_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("metadata json 解析失败，使用空 metadata。metadata={}", metadataJson, e);
            return new LinkedHashMap<>();
        }
    }

    private double calculateMetadataScore(Document document, TravelQuerySignals signals) {
        Map<String, Object> metadata = document.getMetadata();
        double score = 0.0;
        score += 4.0 * intersectionSize(asStringSet(metadata.get("location")), signals.locations());
        score += 2.0 * intersectionSize(asStringSet(metadata.get("tag")), signals.tags());
        String text = document.getText() == null ? "" : document.getText();
        for (String keyword : signals.keywords()) {
            if (text.contains(keyword)) {
                score += 0.5;
            }
        }
        return Math.max(score, 1.0);
    }

    private Set<String> asStringSet(Object value) {
        Set<String> result = new LinkedHashSet<>();
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
        }
        return result;
    }

    private int intersectionSize(Set<String> left, Set<String> right) {
        int count = 0;
        for (String item : left) {
            if (right.contains(item)) {
                count++;
            }
        }
        return count;
    }

    private void merge(Map<String, CandidateDocument> candidates, Document document, double score, String source) {
        String id = document.getId();
        CandidateDocument candidate = candidates.get(id);
        if (candidate == null) {
            candidates.put(id, new CandidateDocument(document, score, new LinkedHashSet<>(Set.of(source))));
            return;
        }
        candidate.add(score, source);
    }

    private String quoteIdentifier(String identifier) {
        if (!identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("非法 PostgreSQL identifier: " + identifier);
        }
        return "\"" + identifier + "\"";
    }

    private static class CandidateDocument {

        private final Document document;
        private final Set<String> sources;
        private double score;

        private CandidateDocument(Document document, double score, Set<String> sources) {
            this.document = document;
            this.score = score;
            this.sources = sources;
        }

        private void add(double addedScore, String source) {
            sources.add(source);
            score += addedScore;
            document.getMetadata().put("hybrid_score", score);
            document.getMetadata().put("retrieval_strategy", String.join("+", sources));
        }

        private double score() {
            return score;
        }

        private Document toDocument() {
            document.getMetadata().put("hybrid_score", score);
            document.getMetadata().put("retrieval_strategy", String.join("+", sources));
            return document.mutate().score(score).metadata(document.getMetadata()).build();
        }
    }

    private record TravelQuerySignals(Set<String> locations, Set<String> tags, Set<String> keywords) {

        private static TravelQuerySignals from(String queryText) {
            String text = queryText == null ? "" : queryText;
            Set<String> locations = collectLocations(text);
            Set<String> tags = collectTags(text);
            Set<String> keywords = new LinkedHashSet<>();
            keywords.addAll(locations);
            keywords.addAll(tags);
            return new TravelQuerySignals(locations, tags, keywords);
        }

        private static Set<String> collectLocations(String text) {
            Set<String> locations = new LinkedHashSet<>();
            for (String location : LOCATION_TERMS) {
                if (text.contains(location)) {
                    locations.add(location);
                }
            }
            return locations;
        }

        private static Set<String> collectTags(String text) {
            Set<String> tags = new LinkedHashSet<>();
            String normalized = text.toLowerCase(Locale.ROOT);
            for (Map.Entry<String, List<String>> entry : TAG_KEYWORDS.entrySet()) {
                for (String keyword : entry.getValue()) {
                    if (normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                        tags.add(entry.getKey());
                        break;
                    }
                }
            }
            return tags;
        }

        private boolean isEmpty() {
            return locations.isEmpty() && tags.isEmpty() && keywords.isEmpty();
        }
    }
}
