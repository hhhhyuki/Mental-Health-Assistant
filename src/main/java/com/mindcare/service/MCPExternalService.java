package com.mindcare.service;

import com.mindcare.util.ExcelWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class MCPExternalService {

    private static final Logger log = LoggerFactory.getLogger(MCPExternalService.class);

    private final ExcelWriter excelWriter;

    @Value("${mcp.email.from}")
    private String emailFrom;

    public MCPExternalService(ExcelWriter excelWriter) {
        this.excelWriter = excelWriter;
    }

    public void sendEmailAlert(String userEmail, String userName, double riskScore, String emotionLabel) {
        log.info("=== MCP Email Alert ===");
        log.info("To: {}", userEmail);
        log.info("Subject: [心理预警] 高风险学生情绪预警");
        log.info("Body: 学生{} (邮箱:{}) 当前情绪状态为: {}, 风险分数: {}",
            userName, userEmail, emotionLabel, String.format("%.2f", riskScore));
        log.info("建议行动: 请及时联系该学生进行心理疏导。");
        log.info("=== End of Alert ===");
    }

    public void writeRecordToExcel(String identifier, String content, String emotionLabel, String riskLevel) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String[] headers = {"时间", "用户", "内容摘要", "情绪标签", "风险等级"};
        String[] row = {timestamp, identifier, truncate(content, 50), emotionLabel, riskLevel};

        try {
            excelWriter.appendRow("对话记录", headers, row);
            log.info("Excel record written for user: {}", identifier);
        } catch (Exception e) {
            log.warn("Failed to write Excel record: {}", e.getMessage());
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}