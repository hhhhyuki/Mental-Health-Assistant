package com.mindcare.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String anonymousId;

    private String role; // "user" or "assistant"

    @Column(length = 4000)
    private String content;

    private String messageType; // "TEXT", "AUDIO", "IMAGE", "VIDEO"

    private String fileUrl;

    private String emotionLabel;

    private Double emotionScore;

    private String riskLevel; // "NONE", "LOW", "MEDIUM", "HIGH"

    private LocalDateTime timestamp = LocalDateTime.now();

    public ChatMessage() {}

    public ChatMessage(Long userId, String role, String content) {
        this.userId = userId;
        this.role = role;
        this.content = content;
        this.messageType = "TEXT";
        this.timestamp = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getAnonymousId() { return anonymousId; }
    public void setAnonymousId(String anonymousId) { this.anonymousId = anonymousId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }
    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
    public String getEmotionLabel() { return emotionLabel; }
    public void setEmotionLabel(String emotionLabel) { this.emotionLabel = emotionLabel; }
    public Double getEmotionScore() { return emotionScore; }
    public void setEmotionScore(Double emotionScore) { this.emotionScore = emotionScore; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}