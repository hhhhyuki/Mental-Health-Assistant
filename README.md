# Mental Health Assistant (智心AI)

面向校园场景的心理健康 AI 智能体助手，基于 Spring Boot + Ollama 本地大模型，提供情绪识别、Agentic RAG 心理咨询、SSE 流式对话、多模态融合、自动预警等功能。

## 技术栈

| 组件 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.2.0 + JDK 17 |
| AI 框架 | Spring AI 0.8.1 |
| 大模型 | Ollama Qwen2.5:7b (本地部署) |
| 向量库 | 内存向量存储 (InMemoryVectorStore) |
| Embedding | bge-m3 (Ollama) |
| 数据库 | H2 (内存模式, 零配置) |
| 权限 | HTTP Basic Auth |

## 快速开始

### 前置要求

- JDK 17+
- Maven 3.8+
- [Ollama](https://ollama.com) 已安装运行

### 拉取模型

```bash
ollama pull qwen2.5:7b
ollama pull bge-m3
```

### 构建运行

```bash
# 打 jar 包
mvn package -DskipTests

# 运行
java -jar target/mindcare-agent-1.0.0.jar
```

服务启动在 `http://localhost:8080`，会自动加载知识库文档到向量存储。

### 默认账号

- 管理员: `admin` / `admin123`

## API 接口

### 对话

```bash
curl -u admin:admin123 \
  -H "Content-Type: application/json" \
  -d '{"text":"我最近失眠很严重怎么办"}' \
  http://localhost:8080/api/chat
```

**响应示例:**

```json
{
  "reply": "失眠是常见的心理困扰...（RAG 检索后的回答）",
  "emotionLabel": "焦虑",
  "emotionScore": 0.85,
  "riskLevel": "MEDIUM"
}
```

### SSE 流式对话

```bash
curl -u admin:admin123 -N \
  -H "Content-Type: application/json" \
  -d '{"text":"我最近压力很大"}' \
  http://localhost:8080/api/chat/stream
```

以 `text/event-stream` 格式逐 token 返回，前端可用 `EventSource` 接收实现打字机效果。

### 认证

```bash
GET /api/auth/check
```

用于前端验证登录状态。

### 上传 (预留接口)

```bash
POST /api/upload/audio
POST /api/upload/image
```

### 管理员

```bash
GET  /api/admin/students
GET  /api/admin/risk-alerts
GET  /api/admin/statistics
```

## Agentic RAG 架构

用户输入经过完整的智能体推理链路：

```
用户输入 → 意图分类
         ├─ CHAT    → 直接闲聊回复（不走 LLM）
         ├─ CONSULT → Query 改写 → 知识库检索 (Top-3) → LLM 生成回答 → 质量校验 → 输出
         └─ RISK    → 触发危机干预流程 + 预警
```

- **意图+改写+推理一次完成**: 单次 LLM 调用同时完成三类任务，减少延迟
- **质量验证回环**: 规则校验 + 最多 3 次重试机制
- **检索上下文扩展**: 命中 chunk + 相邻块拼接，保证语义完整
- **简单聊天检测**: 纯规则匹配，问候语零延迟返回

## 情绪与风险体系

### 融合引擎

多模态情绪融合权重（视觉、语音尚未接入，仅文本生效）：

```
融合情绪 = 视觉(0.5) × 表情标签 + 语音(0.4) × 语调标签 + 文本(0.1) × 语义标签
风险等级 = 取模型输出与关键词匹配的较高等级（保守安全策略）
```

### 情绪分类 (4类)

| 标签 | 含义 |
|------|------|
| 正常 | 情绪稳定 |
| 低落 | 情绪偏低 |
| 焦虑 | 焦虑状态 |
| 高风险 | 需要紧急干预 |

### 风险等级

| 等级 | 触发条件 | 动作 |
|------|---------|------|
| NONE | 正常情绪，无关键词 | 继续对话 |
| LOW | 低落情绪 | 引导话术 |
| MEDIUM | 焦虑/中等关键词匹配 | 引导话术 + Excel 记录 |
| HIGH | 高危关键词/高风险情绪 | 危机话术 + Excel 记录 |

**高危关键词:** 想死、自杀、活不下去、自残、结束生命、不想活了、伤害自己

**危机响应:** 检测到高风险时自动触发，提供全国24小时心理援助热线（400-161-9995）等信息。

## 知识库

5 个心理分类，28 个文档块，启动时自动加载：

- 心理健康基础
- 危机干预
- 咨询伦理
- 放松技巧
- 校园资源

启动时自动分块、生成 embedding 并存入向量库，用于 RAG 检索增强。

## 项目结构

```
src/main/java/com/mindcare/
├── config/          # 安全、Ollama、向量库等配置
├── controller/      # REST API 控制器（聊天、管理、认证）
├── dto/             # 数据传输对象
├── entity/          # JPA 实体（用户、消息、情绪、预警）
├── repository/      # 数据访问层
├── service/         # 核心业务服务
│   ├── AgenticRAGService.java      # 智能体 RAG（意图分类+检索+生成+校验）
│   ├── MentalChatService.java       # 聊天编排
│   ├── EmotionAnalysisService.java  # 文本情绪分析
│   ├── MultiModalFusionEngine.java  # 多模态情绪融合引擎
│   ├── KnowledgeBaseLoader.java     # 启动时加载知识库
│   ├── ModelInferenceService.java   # Ollama LLM 封装
│   └── MCPExternalService.java      # 邮件预警 + Excel 报表
└── util/            # 工具类 (Whisper/MediaPipe/Excel)

knowledge-base/      # 知识库原始文档 (.md)
python-service/      # Python MediaPipe 视觉分析服务（v3 启用）
```

## 版本路线

| 版本 | 功能 | 状态 |
|------|------|------|
| v1 | 纯文本对话 + 基础 RAG + 情绪分析 | ✅ 完成 |
| v2 | SSE 流式输出 + Agentic RAG + 意图分类 + 危机干预 + 多步推理 + 质量验证 | ✅ 完成 |
| v3 | 多模态融合 (Whisper 语音 + MediaPipe 视觉) | 📅 待开发 |
| v4 | MCP 邮件预警 + Excel 报表 + JWT 权限 | 📅 待开发 |
| v5 | LoRA 微调 + 心理咨询专用模型 | 📅 待开发 |

## 许可证

MIT