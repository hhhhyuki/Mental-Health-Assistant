package com.mindcare.dto;

public class EmotionResult {
    private String label;
    private double score;
    private String source; // "text", "voice", "image", "fusion"

    public EmotionResult() {}

    public EmotionResult(String label, double score, String source) {
        this.label = label;
        this.score = score;
        this.source = source;
    }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}