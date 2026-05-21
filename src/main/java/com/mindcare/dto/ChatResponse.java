package com.mindcare.dto;

import java.time.LocalDateTime;

public class ChatResponse {
    private String reply;
    private String emotionLabel;
    private Double emotionScore;
    private String riskLevel;
    private LocalDateTime timestamp;

    public ChatResponse() {}

    public ChatResponse(String reply, String emotionLabel, Double emotionScore, String riskLevel) {
        this.reply = reply;
        this.emotionLabel = emotionLabel;
        this.emotionScore = emotionScore;
        this.riskLevel = riskLevel;
        this.timestamp = LocalDateTime.now();
    }

    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
    public String getEmotionLabel() { return emotionLabel; }
    public void setEmotionLabel(String emotionLabel) { this.emotionLabel = emotionLabel; }
    public Double getEmotionScore() { return emotionScore; }
    public void setEmotionScore(Double emotionScore) { this.emotionScore = emotionScore; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}