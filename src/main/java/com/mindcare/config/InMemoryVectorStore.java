package com.mindcare.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class InMemoryVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryVectorStore.class);

    private final List<DocumentEntry> documents = new CopyOnWriteArrayList<>();
    private final EmbeddingClient embeddingClient;

    public InMemoryVectorStore(EmbeddingClient embeddingClient) {
        this.embeddingClient = embeddingClient;
    }

    private static class DocumentEntry {
        final Document document;
        final float[] embedding;

        DocumentEntry(Document document, float[] embedding) {
            this.document = document;
            this.embedding = embedding;
        }
    }

    @Override
    public void add(List<Document> docs) {
        if (docs == null || docs.isEmpty()) return;

        List<String> texts = docs.stream()
                .map(Document::getContent)
                .collect(Collectors.toList());

        List<List<Double>> rawEmbeddings = embeddingClient.embed(texts);
        List<float[]> embeddings = rawEmbeddings.stream()
                .map(InMemoryVectorStore::toFloatArray)
                .collect(Collectors.toList());

        for (int i = 0; i < docs.size(); i++) {
            documents.add(new DocumentEntry(docs.get(i), embeddings.get(i)));
        }
        log.info("Added {} documents to InMemoryVectorStore (total: {})", docs.size(), documents.size());
    }

    @Override
    public Optional<Boolean> delete(List<String> idFilter) {
        if (idFilter == null || idFilter.isEmpty()) return Optional.of(false);
        boolean removed = documents.removeIf(entry -> idFilter.contains(entry.document.getId()));
        log.info("Deleted documents from InMemoryVectorStore (remaining: {})", documents.size());
        return Optional.of(removed);
    }

    @Override
    public List<Document> similaritySearch(String query) {
        return similaritySearch(SearchRequest.query(query).withTopK(5));
    }

    @Override
    public List<Document> similaritySearch(SearchRequest searchRequest) {
        if (documents.isEmpty()) return Collections.emptyList();

        String query = searchRequest.getQuery();
        int topK = searchRequest.getTopK();

        List<Double> rawEmbedding = embeddingClient.embed(query);
        float[] queryEmbedding = toFloatArray(rawEmbedding);

        List<ScoredResult> scored = new ArrayList<>();
        for (DocumentEntry entry : documents) {
            double similarity = cosineSimilarity(queryEmbedding, entry.embedding);
            scored.add(new ScoredResult(entry.document, similarity));
        }

        scored.sort((a, b) -> Double.compare(b.score, a.score));

        return scored.stream()
                .limit(topK)
                .map(sr -> sr.document)
                .collect(Collectors.toList());
    }

    private static double cosineSimilarity(float[] a, float[] b) {
        double dotProduct = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static float[] toFloatArray(List<Double> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i).floatValue();
        }
        return arr;
    }

    private static class ScoredResult {
        final Document document;
        final double score;

        ScoredResult(Document document, double score) {
            this.document = document;
            this.score = score;
        }
    }
}
