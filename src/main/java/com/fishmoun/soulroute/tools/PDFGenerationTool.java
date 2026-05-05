package com.fishmoun.soulroute.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpRequest;
import com.fishmoun.soulroute.constant.FileConstant;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class PDFGenerationTool {

    private static final Pattern SECTION_PATTERN = Pattern.compile(
            "^(第\\s*[一二三四五六七八九十0-9]+\\s*[天日].*|Day\\s*\\d+.*|DAY\\s*\\d+.*|\\d+[.、].*)$");
    private static final Pattern UNSUPPORTED_CODEPOINTS = Pattern.compile("[\\p{So}\\p{Sm}\\p{Cs}]");
    private static final List<String> LOCATION_TERMS = List.of(
            "上海", "北京", "天津", "重庆", "南京", "苏州", "杭州", "成都", "广州", "深圳",
            "厦门", "青岛", "西安", "长沙", "武汉", "昆明", "大理", "丽江", "三亚", "桂林");

    private static final List<TravelImage> SHANGHAI_IMAGES = List.of(
            new TravelImage(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a1/Lujiazui_from_The_Bund.jpg/960px-Lujiazui_from_The_Bund.jpg",
                    "陆家嘴天际线，从外滩远眺",
                    "Wikimedia Commons / Lujiazui from The Bund.jpg"),
            new TravelImage(
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/5/5f/Lujiazui_skyline_by_night_from_Bund%2C_fully_illuminated.jpg/960px-Lujiazui_skyline_by_night_from_Bund%2C_fully_illuminated.jpg",
                    "外滩视角的陆家嘴夜景",
                    "Wikimedia Commons / Lujiazui skyline by night from Bund.jpg")
    );

    @Tool(description = "Generate a PDF file with given content")
    public String generatePDF(
            @ToolParam(description = "Name of the file to save the generated PDF") String fileName,
            @ToolParam(description = "Content to be included in the PDF") String content) {
        String fileDir = FileConstant.FILE_SAVE_DIR + "/pdf";
        String safeFileName = normalizeFileName(fileName);
        String filePath = fileDir + "/" + safeFileName;
        try {
            FileUtil.mkdir(fileDir);
            try (PdfWriter writer = new PdfWriter(filePath);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {
                PdfFont font = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
                document.setFont(font);

                String normalizedContent = normalizePdfText(content);
                String title = extractTitle(normalizedContent);
                String destination = extractLocation(normalizedContent);
                List<TravelImage> images = selectImages(destination);

                addTitleBlock(document, title);
                addImage(document, images, 0);

                List<Section> sections = splitSections(normalizedContent);
                int imageIndex = 1;
                for (int i = 0; i < sections.size(); i++) {
                    addSection(document, sections.get(i));
                    if (imageIndex < images.size() && i % 2 == 1) {
                        addImage(document, images, imageIndex);
                        imageIndex++;
                    }
                }
            }
            return "PDF generated successfully to: " + filePath;
        } catch (Exception e) {
            return "Error generating PDF: " + e.getMessage();
        }
    }

    private void addTitleBlock(Document document, String title) {
        document.add(new Paragraph(title)
                .setFontSize(20)
                .setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph("旅行手册")
                .setFontSize(12)
                .setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph(" "));
    }

    private void addSection(Document document, Section section) {
        if (section.title() != null && !section.title().isBlank()) {
            document.add(new Paragraph(section.title()).setFontSize(14));
        }
        if (section.body() != null && !section.body().isBlank()) {
            document.add(new Paragraph(section.body()).setFontSize(11));
        }
        document.add(new Paragraph(" "));
    }

    private void addImage(Document document, List<TravelImage> images, int index) {
        if (index < 0 || index >= images.size()) {
            return;
        }
        TravelImage travelImage = images.get(index);
        try {
            byte[] imageBytes = HttpRequest.get(travelImage.url())
                    .header("User-Agent", "Mozilla/5.0 SoulRoute/1.0")
                    .timeout(15000)
                    .execute()
                    .bodyBytes();
            if (!looksLikeImage(imageBytes)) {
                return;
            }

            ImageData imageData = ImageDataFactory.create(imageBytes);
            Image image = new Image(imageData);
            image.setWidth(UnitValue.createPercentValue(100));
            image.setAutoScaleHeight(true);
            document.add(image);
            document.add(new Paragraph(travelImage.caption()).setFontSize(9));
            document.add(new Paragraph("图片来源：" + travelImage.credit()).setFontSize(8));
            document.add(new Paragraph(" "));
        } catch (Exception ignored) {
            // 图片只是辅助内容；下载或解析失败时保留文字版 PDF。
            System.err.println("Skipped PDF image: " + travelImage.url());
        }
    }

    private List<TravelImage> selectImages(String destination) {
        if ("上海".equals(destination)) {
            return SHANGHAI_IMAGES;
        }
        return List.of();
    }

    private boolean looksLikeImage(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return false;
        }
        String prefix = new String(bytes, 0, Math.min(bytes.length, 20), StandardCharsets.ISO_8859_1);
        return bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8
                || prefix.startsWith("\u0089PNG")
                || prefix.startsWith("GIF87a")
                || prefix.startsWith("GIF89a");
    }

    private List<Section> splitSections(String content) {
        List<Section> sections = new ArrayList<>();
        String[] lines = content.split("\\r?\\n");
        String currentTitle = "";
        StringBuilder currentBody = new StringBuilder();
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isBlank()) {
                continue;
            }
            if (SECTION_PATTERN.matcher(line).matches()) {
                if (!currentBody.isEmpty() || !currentTitle.isBlank()) {
                    sections.add(new Section(currentTitle, currentBody.toString().trim()));
                    currentBody.setLength(0);
                }
                currentTitle = line;
                continue;
            }
            if (currentBody.length() > 0) {
                currentBody.append("\n");
            }
            currentBody.append(line);
        }
        if (!currentBody.isEmpty() || !currentTitle.isBlank()) {
            sections.add(new Section(currentTitle, currentBody.toString().trim()));
        }
        if (sections.isEmpty()) {
            sections.add(new Section("行程说明", content));
        }
        return sections;
    }

    private String extractTitle(String content) {
        if (content == null || content.isBlank()) {
            return "旅行攻略";
        }
        String firstLine = content.split("\\r?\\n")[0].trim();
        if (firstLine.length() <= 40) {
            return firstLine;
        }
        return "旅行攻略";
    }

    private String extractLocation(String content) {
        if (content == null) {
            return "";
        }
        Set<String> matches = new LinkedHashSet<>();
        for (String location : LOCATION_TERMS) {
            if (content.contains(location)) {
                matches.add(location);
            }
        }
        if (!matches.isEmpty()) {
            return matches.iterator().next();
        }
        return "";
    }

    private String normalizePdfText(String content) {
        if (content == null || content.isBlank()) {
            return "旅行攻略";
        }
        String normalized = content
                .replace("→", "到")
                .replace("⇒", "到")
                .replace("•", "-")
                .replace("⚠️", "注意：")
                .replace("⚠", "注意：")
                .replace("｜", "/")
                .replace("–", "-")
                .replace("—", "-");
        return UNSUPPORTED_CODEPOINTS.matcher(normalized)
                .replaceAll("")
                .replace("\u0000", "")
                .trim();
    }

    private String normalizeFileName(String fileName) {
        String normalized = fileName == null || fileName.isBlank() ? "旅行攻略.pdf" : fileName.trim();
        normalized = normalized.replace("\\", "_").replace("/", "_");
        if (!normalized.toLowerCase().endsWith(".pdf")) {
            normalized = normalized + ".pdf";
        }
        return normalized;
    }

    private record Section(String title, String body) {
    }

    private record TravelImage(String url, String caption, String credit) {
    }
}
