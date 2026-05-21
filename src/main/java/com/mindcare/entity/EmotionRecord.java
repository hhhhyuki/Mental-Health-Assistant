package com.mindcare.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "emotion_records")
public class EmotionRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Double overallRisk;

    @Column(length = 2000)
    private String emotionTags; // JSON: multimodal fusion result

    private LocalDateTime timestamp = LocalDateTime.now();

    public EmotionRecord() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Double getOverallRisk() { return overallRisk; }
    public void setOverallRisk(Double overallRisk) { this.overallRisk = overallRisk; }
    public String getEmotionTags() { return emotionTags; }
    public void setEmotionTags(String emotionTags) { this.emotionTags = emotionTags; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}