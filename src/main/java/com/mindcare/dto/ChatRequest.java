package com.mindcare.dto;

public class ChatRequest {
    private String text;
    private Long userId;
    private String anonymousId;

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getAnonymousId() { return anonymousId; }
    public void setAnonymousId(String anonymousId) { this.anonymousId = anonymousId; }
}