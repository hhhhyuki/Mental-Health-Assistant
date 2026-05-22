package com.mindcare.dto;

import reactor.core.publisher.Flux;

public class ChatStreamResult {
    private final String emotionLabel;
    private final double emotionScore;
    private final String riskLevel;
    private final Flux<String> tokenStream;

    public ChatStreamResult(String emotionLabel, double emotionScore, String riskLevel, Flux<String> tokenStream) {
        this.emotionLabel = emotionLabel;
        this.emotionScore = emotionScore;
        this.riskLevel = riskLevel;
        this.tokenStream = tokenStream;
    }

    public String getEmotionLabel() { return emotionLabel; }
    public double getEmotionScore() { return emotionScore; }
    public String getRiskLevel() { return riskLevel; }
    public Flux<String> getTokenStream() { return tokenStream; }
}