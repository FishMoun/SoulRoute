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
    private static final Map<String, List<String>> LOCATION_ALIASES = new LinkedHashMap<>();
    private static final Map<String, List<String>> LOCATION_EXPANSIONS = new LinkedHashMap<>();
    private static final Map<String, String> QUERY_REWRITE_REPLACEMENTS = new LinkedHashMap<>();

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

        LOCATION_ALIASES.put("北京", List.of("帝都"));
        LOCATION_ALIASES.put("上海", List.of("魔都"));
        LOCATION_ALIASES.put("广州", List.of("羊城"));
        LOCATION_ALIASES.put("深圳", List.of("鹏城"));
        LOCATION_ALIASES.put("重庆", List.of("山城"));
        LOCATION_ALIASES.put("成都", List.of("蓉城"));
        LOCATION_ALIASES.put("南京", List.of("金陵"));
        LOCATION_ALIASES.put("杭州", List.of("临安"));
        LOCATION_ALIASES.put("西安", List.of("长安"));
        LOCATION_ALIASES.put("济南", List.of("泉城"));
        LOCATION_ALIASES.put("昆明", List.of("春城"));
        LOCATION_ALIASES.put("哈尔滨", List.of("冰城"));

        LOCATION_EXPANSIONS.put("河北", List.of("石家庄", "唐山", "秦皇岛", "承德"));
        LOCATION_EXPANSIONS.put("山西", List.of("太原", "大同", "平遥", "忻州"));
        LOCATION_EXPANSIONS.put("内蒙古", List.of("呼和浩特", "呼伦贝尔", "鄂尔多斯"));
        LOCATION_EXPANSIONS.put("辽宁", List.of("沈阳", "大连", "丹东", "锦州", "营口", "本溪"));
        LOCATION_EXPANSIONS.put("吉林", List.of("长春", "延吉", "通化"));
        LOCATION_EXPANSIONS.put("黑龙江", List.of("哈尔滨", "大庆", "齐齐哈尔", "牡丹江"));
        LOCATION_EXPANSIONS.put("江苏", List.of("南京", "苏州", "扬州", "无锡", "常州"));
        LOCATION_EXPANSIONS.put("浙江", List.of("杭州", "宁波", "绍兴", "嘉兴", "湖州", "温州", "舟山", "台州", "金华", "丽水"));
        LOCATION_EXPANSIONS.put("安徽", List.of("合肥", "黄山", "芜湖", "安庆"));
        LOCATION_EXPANSIONS.put("福建", List.of("福州", "厦门", "泉州", "漳州", "龙岩", "宁德"));
        LOCATION_EXPANSIONS.put("江西", List.of("南昌", "九江", "景德镇", "赣州"));
        LOCATION_EXPANSIONS.put("山东", List.of("济南", "青岛", "烟台", "威海", "淄博", "泰安"));
        LOCATION_EXPANSIONS.put("河南", List.of("郑州", "洛阳", "开封", "安阳", "南阳"));
        LOCATION_EXPANSIONS.put("湖北", List.of("武汉", "宜昌"));
        LOCATION_EXPANSIONS.put("湖南", List.of("长沙", "岳阳"));
        LOCATION_EXPANSIONS.put("广东", List.of("广州", "深圳", "珠海", "佛山", "东莞", "中山", "惠州"));
        LOCATION_EXPANSIONS.put("广西", List.of("南宁", "桂林", "柳州", "北海"));
        LOCATION_EXPANSIONS.put("海南", List.of("海口", "三亚"));
        LOCATION_EXPANSIONS.put("四川", List.of("成都"));
        LOCATION_EXPANSIONS.put("贵州", List.of("贵阳", "遵义"));
        LOCATION_EXPANSIONS.put("云南", List.of("昆明", "大理", "丽江"));
        LOCATION_EXPANSIONS.put("西藏", List.of("拉萨", "日喀则"));
        LOCATION_EXPANSIONS.put("陕西", List.of("西安"));
        LOCATION_EXPANSIONS.put("甘肃", List.of("兰州", "敦煌"));
        LOCATION_EXPANSIONS.put("青海", List.of("西宁"));
        LOCATION_EXPANSIONS.put("宁夏", List.of("银川"));
        LOCATION_EXPANSIONS.put("新疆", List.of("乌鲁木齐", "喀什", "伊犁"));
        LOCATION_EXPANSIONS.put("泰国", List.of("曼谷"));
        LOCATION_EXPANSIONS.put("卡达", List.of("多哈"));

        QUERY_REWRITE_REPLACEMENTS.put("怎么玩", "旅行攻略");
        QUERY_REWRITE_REPLACEMENTS.put("怎么安排", "行程安排");
        QUERY_REWRITE_REPLACEMENTS.put("去哪玩", "景点推荐");
        QUERY_REWRITE_REPLACEMENTS.put("必去", "景点推荐");
        QUERY_REWRITE_REPLACEMENTS.put("必逛", "景点推荐");
        QUERY_REWRITE_REPLACEMENTS.put("必吃", "美食推荐");
        QUERY_REWRITE_REPLACEMENTS.put("吃什么", "美食推荐");
        QUERY_REWRITE_REPLACEMENTS.put("住哪里", "住宿推荐");
        QUERY_REWRITE_REPLACEMENTS.put("怎么去", "交通指南");
        QUERY_REWRITE_REPLACEMENTS.put("防坑", "避坑提醒");
        QUERY_REWRITE_REPLACEMENTS.put("避雷", "避坑提醒");
        QUERY_REWRITE_REPLACEMENTS.put("3天", "三日");
        QUERY_REWRITE_REPLACEMENTS.put("三天", "三日");
        QUERY_REWRITE_REPLACEMENTS.put("2天", "两日");
        QUERY_REWRITE_REPLACEMENTS.put("两天", "两日");
        QUERY_REWRITE_REPLACEMENTS.put("1天", "一日");
        QUERY_REWRITE_REPLACEMENTS.put("一天", "一日");
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
    private final boolean queryExpansionEnabled;
    private final int maxVectorQueries;
    private final QueryUnderstandingService queryUnderstandingService;

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
                                         double metadataWeight,
                                         boolean queryExpansionEnabled,
                                         int maxVectorQueries,
                                         QueryUnderstandingService queryUnderstandingService) {
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
        this.queryExpansionEnabled = queryExpansionEnabled;
        this.maxVectorQueries = Math.max(1, maxVectorQueries);
        this.queryUnderstandingService = queryUnderstandingService;
    }

    @Override
    public List<Document> retrieve(Query query) {
        String queryText = query.text();
        QueryUnderstanding understanding = queryUnderstandingService.understand(queryText);
        QueryExpansion expansion = QueryExpansion.from(queryText, queryExpansionEnabled, maxVectorQueries, understanding);
        TravelQuerySignals signals = expansion.signals();
        Map<String, CandidateDocument> candidates = new LinkedHashMap<>();

        for (int i = 0; i < expansion.vectorQueries().size(); i++) {
            String vectorQuery = expansion.vectorQueries().get(i);
            double queryWeight = vectorQueryWeight(i);
            for (Document document : retrieveByVector(vectorQuery)) {
                double vectorScore = document.getScore() == null ? 0.7 : document.getScore();
                merge(candidates, document, vectorScore * vectorWeight * queryWeight, sourceName("vector", i));
            }
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
        log.info("混合检索完成，query={}, llmSlots={}, rewrittenQueries={}, location={}, expandedLocation={}, tag={}, 返回 chunk 数={}",
                queryText, understanding.hasSignals(), expansion.vectorQueries(), signals.primaryLocations(), signals.locations(), signals.tags(), documents.size());
        return documents;
    }

    private double vectorQueryWeight(int queryIndex) {
        return Math.max(0.55, 1.0 - queryIndex * 0.12);
    }

    private String sourceName(String source, int queryIndex) {
        return queryIndex == 0 ? source : source + "_expanded";
    }

    private List<Document> retrieveByVector(String queryText) {
        double effectiveThreshold = similarityThreshold;
        if (queryText != null && queryText.trim().length() <= 4) {
            effectiveThreshold = Math.min(similarityThreshold, 0.3);
        }
        SearchRequest searchRequest = SearchRequest.builder()
                .query(queryText)
                .topK(vectorTopK)
                .similarityThreshold(effectiveThreshold)
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
        String orderClause = "";
        List<Object> orderParams = new ArrayList<>();
        String primaryKeyword = !signals.primaryLocations().isEmpty()
            ? signals.primaryLocations().iterator().next()
            : !signals.locations().isEmpty()
            ? signals.locations().iterator().next()
            : (signals.keywords().isEmpty() ? "" : signals.keywords().iterator().next());
        if (StringUtils.hasText(primaryKeyword)) {
            orderClause = "ORDER BY (metadata @> ?::jsonb) DESC, (metadata->>'filename' ILIKE ?) DESC, (content ILIKE ?) DESC";
            orderParams.add(jsonbArrayContains("location", primaryKeyword));
            String keywordPattern = "%" + primaryKeyword + "%";
            orderParams.add(keywordPattern);
            orderParams.add(keywordPattern);
        }
        parameters.addAll(orderParams);
        parameters.add(metadataTopK);
        String sql = """
            SELECT id, content, metadata::text
            FROM %s
            WHERE %s
            %s
            LIMIT ?
            """.formatted(qualifiedTableName, String.join(" OR ", whereParts), orderClause);
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
        score += 4.0 * intersectionSize(asStringSet(metadata.get("location")), signals.primaryLocations());
        score += 2.0 * intersectionSize(asStringSet(metadata.get("location")), signals.expandedLocations());
        score += 1.5 * intersectionSize(asStringSet(metadata.get("location")), signals.aliasResolvedLocations());
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

    private record QueryExpansion(String originalQuery, List<String> vectorQueries, TravelQuerySignals signals) {

        private static QueryExpansion from(String queryText,
                                           boolean enabled,
                                           int maxVectorQueries,
                                           QueryUnderstanding understanding) {
            String text = queryText == null ? "" : queryText;
            String rewritten = rewrite(text);
            if (understanding != null && StringUtils.hasText(understanding.rewrittenQuery())) {
                rewritten = understanding.rewrittenQuery();
            }
            TravelQuerySignals signals = TravelQuerySignals.from(text, rewritten, understanding);
            List<String> vectorQueries = enabled
                    ? buildVectorQueries(text, rewritten, signals, understanding, maxVectorQueries)
                    : List.of(text);
            return new QueryExpansion(text, vectorQueries, signals);
        }

        private static String rewrite(String text) {
            String rewritten = text == null ? "" : text.trim();
            rewritten = rewritten
                    .replaceAll("\\s+", " ")
                    .replace("帮我", "")
                    .replace("请", "")
                    .replace("一下", "")
                    .replace("推荐下", "推荐")
                    .trim();
            for (Map.Entry<String, String> entry : QUERY_REWRITE_REPLACEMENTS.entrySet()) {
                rewritten = rewritten.replace(entry.getKey(), entry.getValue());
            }
            return rewritten.isBlank() ? text : rewritten;
        }

        private static List<String> buildVectorQueries(String originalText,
                                                       String rewrittenText,
                                                       TravelQuerySignals signals,
                                                       QueryUnderstanding understanding,
                                                       int maxVectorQueries) {
            LinkedHashSet<String> queries = new LinkedHashSet<>();
            addIfText(queries, originalText);
            addIfText(queries, rewrittenText);

            for (String location : signals.primaryLocations()) {
                queries.add(location + " 旅行攻略");
                if (!signals.tags().isEmpty()) {
                    queries.add(location + " " + String.join(" ", signals.tags()));
                }
            }
            for (String location : signals.expandedLocations()) {
                queries.add(location + " 旅行攻略");
            }
            if (signals.primaryLocations().isEmpty() && !signals.tags().isEmpty()) {
                queries.add(String.join(" ", signals.tags()) + " 旅行攻略");
            }
            if (understanding != null && understanding.hasSignals()) {
                for (String destination : understanding.destinations()) {
                    queries.add(destination + " " + String.join(" ", signals.tags()) + " 旅行攻略");
                }
                for (String destination : understanding.expandedDestinations()) {
                    queries.add(destination + " 旅行攻略");
                }
            }

            return queries.stream()
                    .filter(StringUtils::hasText)
                    .limit(maxVectorQueries)
                    .toList();
        }

        private static void addIfText(Set<String> queries, String text) {
            if (StringUtils.hasText(text)) {
                queries.add(text.trim());
            }
        }
    }

    private record TravelQuerySignals(Set<String> primaryLocations,
                                      Set<String> expandedLocations,
                                      Set<String> aliasResolvedLocations,
                                      Set<String> locations,
                                      Set<String> tags,
                                      Set<String> keywords) {

        private static TravelQuerySignals from(String originalText, String rewrittenText, QueryUnderstanding understanding) {
            String combined = originalText + "\n" + rewrittenText;
            Set<String> primaryLocations = collectLocations(combined);
            Set<String> aliasResolvedLocations = collectAliasLocations(combined);
            if (understanding != null) {
                primaryLocations.addAll(understanding.destinations());
            }
            primaryLocations.addAll(aliasResolvedLocations);
            Set<String> expandedLocations = expandLocations(primaryLocations);
            if (understanding != null) {
                expandedLocations.addAll(understanding.expandedDestinations());
            }
            Set<String> locations = new LinkedHashSet<>();
            locations.addAll(primaryLocations);
            locations.addAll(expandedLocations);
            Set<String> tags = collectTags(combined);
            if (understanding != null) {
                tags.addAll(normalizeInterests(understanding.interests()));
            }
            Set<String> keywords = new LinkedHashSet<>();
            keywords.addAll(locations);
            keywords.addAll(tags);
            keywords.addAll(extractUsefulKeywords(rewrittenText));
            if (understanding != null) {
                addIfText(keywords, understanding.budget());
                addIfText(keywords, understanding.pace());
                addIfText(keywords, understanding.companion());
                addIfText(keywords, understanding.outputFormat());
                if (understanding.days() != null) {
                    keywords.add(understanding.days() + "天");
                    keywords.add(toChineseDayKeyword(understanding.days()));
                }
            }
            return new TravelQuerySignals(primaryLocations, expandedLocations, aliasResolvedLocations, locations, tags, keywords);
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

        private static Set<String> collectAliasLocations(String text) {
            Set<String> locations = new LinkedHashSet<>();
            for (Map.Entry<String, List<String>> entry : LOCATION_ALIASES.entrySet()) {
                for (String alias : entry.getValue()) {
                    if (text.contains(alias)) {
                        locations.add(entry.getKey());
                        break;
                    }
                }
            }
            return locations;
        }

        private static Set<String> expandLocations(Set<String> locations) {
            Set<String> expanded = new LinkedHashSet<>();
            for (String location : locations) {
                List<String> children = LOCATION_EXPANSIONS.get(location);
                if (children != null) {
                    expanded.addAll(children);
                }
            }
            return expanded;
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

        private static Set<String> normalizeInterests(List<String> interests) {
            Set<String> tags = new LinkedHashSet<>();
            if (interests == null) {
                return tags;
            }
            for (String interest : interests) {
                if (!StringUtils.hasText(interest)) {
                    continue;
                }
                String normalized = interest.trim();
                boolean mapped = false;
                for (Map.Entry<String, List<String>> entry : TAG_KEYWORDS.entrySet()) {
                    if (entry.getKey().equals(normalized) || entry.getValue().contains(normalized)) {
                        tags.add(entry.getKey());
                        mapped = true;
                        break;
                    }
                }
                if (!mapped) {
                    tags.add(normalized);
                }
            }
            return tags;
        }

        private static void addIfText(Set<String> values, String value) {
            if (StringUtils.hasText(value)) {
                values.add(value.trim());
            }
        }

        private static String toChineseDayKeyword(int days) {
            return switch (days) {
                case 1 -> "一日";
                case 2 -> "两日";
                case 3 -> "三日";
                case 4 -> "四日";
                case 5 -> "五日";
                default -> days + "日";
            };
        }

        private static Set<String> extractUsefulKeywords(String text) {
            Set<String> keywords = new LinkedHashSet<>();
            if (!StringUtils.hasText(text)) {
                return keywords;
            }
            for (String token : text.split("[\\s,，。；;、]+")) {
                String normalized = token.trim();
                if (normalized.length() >= 2 && normalized.length() <= 12) {
                    keywords.add(normalized);
                }
            }
            return keywords;
        }

        private boolean isEmpty() {
            return locations.isEmpty() && tags.isEmpty() && keywords.isEmpty();
        }
    }
}
