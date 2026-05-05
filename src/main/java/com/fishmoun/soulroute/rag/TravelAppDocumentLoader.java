package com.fishmoun.soulroute.rag;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Component
class TravelAppDocumentLoader {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^(第?[一二三四五六七八九十0-9]+[、.．]|#+\\s+|[一二三四五六七八九十0-9]+\\.|[【《].+[】》]).*");
    private static final List<String> LOCATION_TERMS = List.of(
            "北京", "天津", "上海", "重庆", "河北", "山西", "内蒙古", "辽宁", "吉林", "黑龙江", "江苏", "浙江", "安徽", "福建", "江西", "山东",
            "河南", "湖北", "湖南", "广东", "广西", "海南", "四川", "贵州", "云南", "西藏", "陕西", "甘肃", "青海", "宁夏", "新疆",
            "香港", "澳门", "台湾", "哈尔滨", "长春", "沈阳", "大连", "丹东", "延吉", "大庆", "齐齐哈尔", "牡丹江", "锦州", "营口", "通化", "本溪",
            "南京", "苏州", "杭州", "宁波", "绍兴", "嘉兴", "湖州", "温州", "舟山", "台州", "金华", "丽水", "合肥", "黄山", "芜湖", "安庆",
            "福州", "厦门", "泉州", "漳州", "龙岩", "宁德", "南昌", "九江", "景德镇", "赣州", "济南", "青岛", "烟台", "威海", "淄博", "泰安",
            "武汉", "宜昌", "长沙", "岳阳", "郑州", "洛阳", "开封", "安阳", "南阳", "广州", "深圳", "珠海", "佛山", "东莞", "中山", "惠州",
            "南宁", "桂林", "柳州", "北海", "三亚", "海口", "成都", "重庆", "昆明", "大理", "丽江", "贵阳", "遵义", "拉萨", "日喀则",
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

    private final ResourcePatternResolver resourcePatternResolver;
    private final String documentsLocation;
    private final int chunkSize;
    private final int chunkOverlapChars;

    TravelAppDocumentLoader(ResourcePatternResolver resourcePatternResolver,
                            @Value("${soulroute.rag.documents-location:classpath*:com/fishmoun/soulroute/data/**/*.docx}") String documentsLocation,
                            @Value("${soulroute.rag.chunk-size:800}") int chunkSize,
                            @Value("${soulroute.rag.chunk-overlap-chars:120}") int chunkOverlapChars) {
        ZipSecureFile.setMinInflateRatio(0.001);
        this.resourcePatternResolver = resourcePatternResolver;
        this.documentsLocation = documentsLocation;
        this.chunkSize = chunkSize;
        this.chunkOverlapChars = Math.max(0, chunkOverlapChars);
    }

    public List<Document> loadAndSplitMarkdowns() {
        List<TravelSourceDocument> sourceDocuments = loadSourceDocuments();
        List<Document> chunks = new ArrayList<>();
        for (TravelSourceDocument sourceDocument : sourceDocuments) {
            List<String> textChunks = splitSemanticChunks(sourceDocument.paragraphs());
            for (int i = 0; i < textChunks.size(); i++) {
                String text = textChunks.get(i);
                Map<String, Object> metadata = buildMetadata(sourceDocument, text, i);
                chunks.add(Document.builder()
                        .id(buildChunkId(sourceDocument.fileName(), i))
                        .text(text)
                        .metadata(metadata)
                        .build());
            }
        }
        log.info("旅行攻略文档加载完成，源文档数: {}, chunk 数: {}", sourceDocuments.size(), chunks.size());
        return chunks;
    }

    private List<TravelSourceDocument> loadSourceDocuments() {
        List<TravelSourceDocument> documents = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources(documentsLocation);
            if (resources.length == 0) {
                log.warn("未找到旅行攻略文档，跳过向量索引构建。pattern={}", documentsLocation);
                return documents;
            }
            for (Resource resource : resources) {
                String fileName = resource.getFilename();
                if (fileName == null) {
                    continue;
                }
                try {
                    String lowerName = fileName.toLowerCase(Locale.ROOT);
                    if (lowerName.endsWith(".docx")) {
                        documents.add(loadDocx(resource));
                    } else if (lowerName.endsWith(".md")) {
                        documents.addAll(loadMarkdown(resource));
                    }
                } catch (IOException | RuntimeException e) {
                    log.warn("旅行攻略文档读取失败，已跳过。file={}", resourcePath(resource), e);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("旅行攻略文档加载失败", e);
        }
        return documents;
    }

    private TravelSourceDocument loadDocx(Resource resource) throws IOException {
        List<String> paragraphs = new ArrayList<>();
        try (InputStream inputStream = resource.getInputStream();
             XWPFDocument document = new XWPFDocument(inputStream)) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                addNormalized(paragraphs, paragraph.getText());
            }
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    List<String> cells = new ArrayList<>();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        String text = normalize(cell.getText());
                        if (!text.isBlank()) {
                            cells.add(text);
                        }
                    }
                    addNormalized(paragraphs, String.join(" | ", cells));
                }
            }
        }
        return new TravelSourceDocument(resource.getFilename(), resourcePath(resource), paragraphs);
    }

    private List<TravelSourceDocument> loadMarkdown(Resource resource) throws IOException {
        MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                .withHorizontalRuleCreateDocument(true)
                .withIncludeCodeBlock(false)
                .withIncludeBlockquote(false)
                .withAdditionalMetadata("filename", resource.getFilename())
                .build();
        MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);
        List<TravelSourceDocument> documents = new ArrayList<>();
        for (Document document : reader.get()) {
            documents.add(new TravelSourceDocument(resource.getFilename(), resourcePath(resource), List.of(document.getText())));
        }
        return documents;
    }

    private List<String> splitSemanticChunks(List<String> paragraphs) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (paragraph.isBlank()) {
                continue;
            }
            boolean startsNewTopic = isHeading(paragraph) && current.length() >= chunkSize / 3;
            boolean exceedsTarget = current.length() + paragraph.length() + 2 > chunkSize;
            if (current.length() > 0 && (startsNewTopic || exceedsTarget)) {
                appendChunk(chunks, current.toString());
                current.setLength(0);
                String overlap = tailOverlap(chunks.get(chunks.size() - 1));
                if (!overlap.isBlank() && !startsNewTopic) {
                    current.append(overlap).append("\n");
                }
            }
            current.append(paragraph).append("\n");
        }
        if (current.length() > 0) {
            appendChunk(chunks, current.toString());
        }
        return chunks;
    }

    private Map<String, Object> buildMetadata(TravelSourceDocument sourceDocument, String text, int chunkIndex) {
        String fileName = sourceDocument.fileName();
        String sourcePath = sourceDocument.sourcePath();
        Set<String> locations = new LinkedHashSet<>();
        collectMatches(locations, LOCATION_TERMS, sourcePath);
        collectMatches(locations, LOCATION_TERMS, fileName);
        if (locations.isEmpty()) {
            collectMatches(locations, LOCATION_TERMS, text);
        }

        Set<String> tags = new LinkedHashSet<>();
        tags.add("旅行攻略");
        for (Map.Entry<String, List<String>> entry : TAG_KEYWORDS.entrySet()) {
            if (containsAny(text, entry.getValue()) || containsAny(fileName, entry.getValue())) {
                tags.add(entry.getKey());
            }
        }
        addRegionTags(tags, sourcePath);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("location", new ArrayList<>(locations));
        metadata.put("tag", new ArrayList<>(tags));
        metadata.put("source", sourcePath);
        metadata.put("filename", fileName);
        metadata.put("chunk_index", chunkIndex);
        return metadata;
    }

    private void addRegionTags(Set<String> tags, String sourcePath) {
        Collection<String> regions = Arrays.asList("东北地区", "华北地区", "华东地区", "华中地区", "华南地区", "西北地区", "西南地区", "国外热门旅游地");
        for (String region : regions) {
            if (sourcePath.contains(region)) {
                tags.add(region);
            }
        }
    }

    private void appendChunk(List<String> chunks, String text) {
        String normalized = normalize(text);
        if (!normalized.isBlank()) {
            chunks.add(normalized);
        }
    }

    private String tailOverlap(String text) {
        if (chunkOverlapChars <= 0 || text.length() <= chunkOverlapChars) {
            return "";
        }
        return text.substring(text.length() - chunkOverlapChars);
    }

    private boolean isHeading(String paragraph) {
        String text = paragraph.trim();
        return text.length() <= 80 && HEADING_PATTERN.matcher(text).matches();
    }

    private void collectMatches(Set<String> result, List<String> candidates, String text) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                result.add(candidate);
            }
        }
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private void addNormalized(List<String> paragraphs, String text) {
        String normalized = normalize(text);
        if (!normalized.isBlank()) {
            paragraphs.add(normalized);
        }
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\u00A0', ' ')
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String resourcePath(Resource resource) throws IOException {
        try {
            URI uri = resource.getURI();
            return uri.toString();
        } catch (IOException e) {
            return resource.getDescription();
        }
    }

    private String buildChunkId(String filename, int chunkIndex) {
        String normalizedFilename = filename.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]", "_");
        return "travel:" + normalizedFilename + ":" + chunkIndex;
    }

    private record TravelSourceDocument(String fileName, String sourcePath, List<String> paragraphs) {
    }
}
