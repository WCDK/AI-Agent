package com.wcdk.ai.agent.document;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
@Component
public class DocumentParser {

    public String parse(Path filePath, String originalFileName) {
        if (filePath == null || !Files.exists(filePath)) {
            throw new IllegalArgumentException("待解析文档不存在。");
        }

        var extension = extensionOf(originalFileName != null ? originalFileName : filePath.getFileName().toString());
        return switch (extension) {
            case "txt", "md", "json", "csv", "java", "xml", "yaml", "yml", "properties", "log" -> readText(filePath);
            case "pdf" -> readPdf(filePath);
            case "docx" -> readDocx(filePath);
            default -> throw new IllegalArgumentException("暂不支持的文档类型：" + extension);
        };
    }

    private String readText(Path filePath) {
        try {
            return Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("读取文本文件失败：" + filePath, exception);
        }
    }

    private String readPdf(Path filePath) {
        try (var document = Loader.loadPDF(filePath.toFile())) {
            return new PDFTextStripper().getText(document);
        } catch (IOException exception) {
            throw new IllegalStateException("解析 PDF 失败：" + filePath, exception);
        }
    }

    private String readDocx(Path filePath) {
        try (InputStream inputStream = Files.newInputStream(filePath);
             XWPFDocument document = new XWPFDocument(inputStream)) {
            var builder = new StringBuilder();
            for (var paragraph : document.getParagraphs()) {
                if (StringUtils.hasText(paragraph.getText())) {
                    builder.append(paragraph.getText()).append(System.lineSeparator());
                }
            }
            return builder.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("解析 DOCX 失败：" + filePath, exception);
        }
    }

    private String extensionOf(String fileName) {
        var index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }
}
