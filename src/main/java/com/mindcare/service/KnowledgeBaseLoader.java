package com.mindcare.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class KnowledgeBaseLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseLoader.class);

    private final VectorStore vectorStore;

    @Value("classpath:knowledge-base/*.md")
    private Resource[] knowledgeResources;

    public KnowledgeBaseLoader(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(String... args) {
        List<Document> allChunks = new ArrayList<>();

        for (Resource resource : knowledgeResources) {
            try {
                String filename = resource.getFilename();
                if (filename == null) continue;

                String content;
                try (InputStream is = resource.getInputStream()) {
                    content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }

                List<Document> chunks = chunkDocument(filename, content);
                allChunks.addAll(chunks);
                log.info("Loaded {} chunks from {}", chunks.size(), filename);
            } catch (Exception e) {
                log.warn("Failed to load knowledge resource: {}", resource.getFilename(), e);
            }
        }

        if (!allChunks.isEmpty()) {
            vectorStore.add(allChunks);
            log.info("Knowledge base loaded: {} chunks from {} files", allChunks.size(), knowledgeResources.length);
        } else {
            log.warn("No knowledge base documents loaded");
        }
    }

    private List<Document> chunkDocument(String filename, String content) {
        List<Document> chunks = new ArrayList<>();

        String category = filename.replace(".md", "");
        String baseName = switch (category) {
            case "psychology_basics" -> "心理健康基础";
            case "crisis_intervention" -> "危机干预";
            case "counseling_ethics" -> "咨询伦理";
            case "relaxation_techniques" -> "放松技巧";
            case "campus_resources" -> "校园资源";
            default -> category;
        };

        // Split by ## headings (secondary sections)
        Pattern pattern = Pattern.compile("(^|\\n)(## [^\\n]+\\n)(.+?)(?=\\n## |\\Z)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);

        int chunkIndex = 0;
        while (matcher.find()) {
            String heading = matcher.group(2).trim();
            String body = matcher.group(3).trim();
            String chunkContent = heading + "\n" + body;
            if (chunkContent.length() < 20) continue;

            // If a section is too long, split by paragraphs
            if (chunkContent.length() > 1500) {
                String[] paragraphs = chunkContent.split("\n\n");
                StringBuilder currentChunk = new StringBuilder();
                for (String para : paragraphs) {
                    if (currentChunk.length() + para.length() > 1500 && currentChunk.length() > 0) {
                        chunks.add(createDocument(baseName, filename, chunkIndex++, currentChunk.toString()));
                        currentChunk = new StringBuilder();
                    }
                    currentChunk.append(para).append("\n\n");
                }
                if (currentChunk.length() > 0) {
                    chunks.add(createDocument(baseName, filename, chunkIndex++, currentChunk.toString()));
                }
            } else {
                chunks.add(createDocument(baseName, filename, chunkIndex++, chunkContent));
            }
        }

        // If no ## headings found, use the whole document as one chunk
        if (chunks.isEmpty()) {
            chunks.add(createDocument(baseName, filename, 0, content));
        }

        return chunks;
    }

    private Document createDocument(String baseName, String filename, int index, String content) {
        return new Document(content, Map.of(
            "category", baseName,
            "source", filename,
            "chunkIndex", index
        ));
    }
}