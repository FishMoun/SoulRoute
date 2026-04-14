# SoulRoute 🌍✈️

一个基于 Spring AI 和大语言模型的**智能旅行规划助手系统**。通过对话式交互，为用户提供专业的旅行规划建议、行程安排、预算管理和避坑指南。

## 📋 项目概述

**SoulRoute** 是一个创新的旅行规划平台，融合了先进的 AI 技术，能够理解用户需求并生成个性化的旅行方案。系统支持多种交互模式（对话、知识库检索、工具调用等），为自由行、情侣出游、家庭出游、朋友结伴和商务差旅等五大场景提供专业指导。

### 核心特性

- 🤖 **AI 驱动的对话引擎** - 基于阿里云 DashScope（通义千问） LLM
- 💾 **对话记忆管理** - 持久化用户对话历史，支持多轮交互
- 📚 **检索增强生成 (RAG)** - 集成阿里云知识库，获取中国旅行攻略
- 🛠️ **工具系统集成** - 支持网页搜索、网页爬取、文件操作、PDF生成等工具
- 🔌 **MCP 客户端支持** - 集成 Model Context Protocol 实现扩展功能
- 📊 **结构化输出** - 支持生成旅行报告、预算拆分、行程建议等结构化数据
- 🌐 **跨场景适配** - 针对五种旅行场景的个性化规划策略
- 📖 **API 文档** - 集成 Knife4j/Swagger-UI 实现在线 API 文档

## 🛠️ 技术栈

### 后端框架
- **Java 21** - 编程语言
- **Spring Boot 3.4.4** - 应用框架
- **Spring AI 1.0.0** - AI 集成框架
- **Spring AI Alibaba 1.0.0.2** - 阿里云 AI 集成

### AI 服务
- **DashScope SDK 2.19.1** - 阿里云灵积大模型服务
- **通义千问 (Qwen-Plus)** - LLM 模型
- **LangChain4j 1.0.0-beta2** - 大语言模型编程框架

### 数据与存储
- **PostgreSQL** - 向量数据库 (PGVector)
- **PGVector Store** - 向量存储和相似度搜索

### 工具库
- **Hutool 5.8.37** - Java 工具集
- **Jsoup 1.19.1** - HTML 解析库
- **iText-core 9.1.0** - PDF 生成库
- **Kryo 5.6.2** - 高性能序列化库
- **Lombok 1.18.36** - Java 代码简化

### API 文档
- **Knife4j 4.4.0** - API 文档与测试工具
- **Springdoc-OpenAPI** - OpenAPI 3.0 规范支持

## 📦 项目结构

```
SoulRoute/
├── src/main/java/com/fishmoun/soulroute/
│   ├── SoulRouteApplication.java          # 主应用程序
│   ├── app/
│   │   └── TravelApp.java                 # 核心旅行规划应用
│   ├── controller/
│   │   └── HealthController.java          # 健康检查端点
│   ├── config/
│   │   ├── TravelAppRagCloudAdvisorConfig.java  # RAG 配置
│   │   ├── ToolRegistration.java          # 工具注册配置
│   │   └── CorsConfig.java                # CORS 跨域配置
│   ├── tools/
│   │   ├── WebSearchTool.java             # 网页搜索工具
│   │   ├── WebScrapingTool.java           # 网页爬取工具
│   │   ├── FileOperationTool.java         # 文件操作工具
│   │   ├── PDFGenerationTool.java         # PDF 生成工具
│   │   ├── ResourceDownloadTool.java      # 资源下载工具
│   │   └── TerminalOperationTool.java     # 终端操作工具
│   ├── chatmemory/
│   │   └── FileBasedChatMemory.java       # 文件持久化对话记忆
│   ├── rag/
│   │   ├── TravelAppDocumentLoader.java   # 文档加载器
│   │   └── TravelAppVectorStoreConfig.java # 向量存储配置
│   ├── advisor/
│   │   └── MyLoggerAdvisor.java           # 日志记录顾问
│   └── constant/
│       └── FileConstant.java              # 文件路径常量
├── src/main/resources/
│   ├── application.yml                    # 主配置文件
│   ├── application-local.yml              # 本地配置文件
│   ├── mcp-servers.json                   # MCP 服务器配置
│   └── static/templates/                  # 静态资源与模板
├── pom.xml                                # Maven 项目配置
└── README.md                              # 项目文档
```

## 🚀 快速开始

### 前置条件

- **Java 21** 或更高版本
- **Maven 3.6+**
- **PostgreSQL 12+** (用于向量数据库)
- **阿里云 DashScope API Key** (获取方式: https://dashscope.aliyun.com)

### 环境配置

1. **克隆项目**
   ```bash
   git clone <repository-url>
   cd SoulRoute
   ```

2. **配置环境变量**
   ```bash
   # 设置阿里云 API Key
   export DASHSCOPE_API_KEY=your_api_key_here
   
   # 或在 application.yml 中配置
   spring:
     ai:
       dashscope:
         api-key: your_api_key_here
   ```

3. **数据库配置**
   
   在 `src/main/resources/application-local.yml` 中配置 PostgreSQL 连接：
   ```yaml
   spring:
     datasource:
       url: jdbc:postgresql://localhost:5432/soulroute
       username: postgres
       password: your_password
     jpa:
       hibernate:
         ddl-auto: update
   ```

### 编译与运行

1. **编译项目**
   ```bash
   mvn clean compile
   ```

2. **构建项目**
   ```bash
   mvn clean package
   ```

3. **运行应用**
   ```bash
   # 方式一: 使用 Maven 运行
   mvn spring-boot:run
   
   # 方式二: 运行 JAR 包
   java -jar target/soul-route-0.0.1-SNAPSHOT.jar
   ```

4. **验证应用**
   ```bash
   # 健康检查
   curl http://localhost:8123/api/health
   
   # 查看 API 文档
   # Swagger UI: http://localhost:8123/api/swagger-ui.html
   # Knife4j: http://localhost:8123/api/doc.html
   ```

## 📖 API 文档

### 基础 URL
```
http://localhost:8123/api
```

### 端点说明

#### 1. 健康检查
```http
GET /health
```
**响应:**
```json
{
  "status": "ok"
}
```

#### 2. 基础对话
```http
POST /travel/chat
```
**参数:**
```json
{
  "message": "我想去云南旅游，请帮我规划一个5天的行程",
  "chatId": "user_123"
}
```

#### 3. 生成旅行报告
```http
POST /travel/chat-with-report
```
**参数:**
```json
{
  "message": "我是一个独自旅行者，想去西安游玩",
  "chatId": "user_123"
}
```

#### 4. 知识库检索增强对话
```http
POST /travel/chat-with-rag
```
**参数:**
```json
{
  "message": "三亚有哪些必去景点?",
  "chatId": "user_123"
}
```

#### 5. 工具调用对话
```http
POST /travel/chat-with-tools
```
**参数:**
```json
{
  "message": "帮我搜索厦门的美食推荐",
  "chatId": "user_123"
}
```

#### 6. MCP 工具对话
```http
POST /travel/chat-with-mcp
```
**参数:**
```json
{
  "message": "根据网络搜索信息规划我的旅行",
  "chatId": "user_123"
}
```

## 🎯 使用场景

系统针对五种主要旅行场景进行了优化：

### 1. 自由行 🧑‍🤝‍🧑
- 重点信息: 目的地、出发地、天数、预算、兴趣偏好
- 轻松游 vs 深度游选择
- **输出:** 详细行程单、景点攻略、美食推荐

### 2. 情侣出游 💕
- 重点信息: 浪漫氛围、拍照打卡、特色住宿、美食体验
- 行程节奏调整
- **输出:** 浪漫路线、拍照地点、烛光晚餐推荐

### 3. 家庭出游 👨‍👩‍👧‍👦
- 重点信息: 同行人员 (老人/小孩)、舒适度、交通便利性
- 安全性与亲子项目
- **输出:** 亲子行程、便利交通方案、安全提示

### 4. 朋友结伴 👯
- 重点信息: 人数、游玩偏好、娱乐项目、夜生活、性价比
- 分工建议
- **输出:** 团体行程、娱乐安排、AA 预算方案

### 5. 商务差旅 💼
- 重点信息: 出差城市、停留时间、会议地点、可自由时段
- 高效出行与体验平衡
- **输出:** 高效行程、会议地点周边、短途休闲方案

## 🔌 功能模块详解

### 1. 对话引擎 (TravelApp)
核心应用程序，提供多种交互模式:
- **基础对话** - 标准 AI 对话
- **对话记忆** - 维护多轮对话历史
- **RAG 检索** - 集成知识库信息
- **工具调用** - 执行外部工具
- **MCP 集成** - 扩展功能支持

### 2. 工具系统
集成多个外部工具:
- **网页搜索 (WebSearchTool)** - 百度搜索引擎集成
- **网页爬取 (WebScrapingTool)** - Jsoup HTML 解析
- **文件操作 (FileOperationTool)** - 本地文件处理
- **PDF 生成 (PDFGenerationTool)** - iText PDF 创建
- **资源下载 (ResourceDownloadTool)** - 网络资源下载
- **终端操作 (TerminalOperationTool)** - 系统命令执行

### 3. 数据持久化
- **对话记忆** - 文件系统持久化用户对话
- **向量存储** - PGVector 存储文档向量
- **知识库** - 阿里云知识库集成

### 4. API 文档
- **Swagger UI** - 标准 OpenAPI 3.0 文档
- **Knife4j** - 增强的 API 文档与测试工具

## ⚙️ 配置说明

### application.yml 主要配置

```yaml
spring:
  application:
    name: SoulRoute
  ai:
    # 阿里云 DashScope 配置
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}  # API Key
      chat:
        options:
          model: qwen-plus             # 使用通义千问 Plus 模型
    # MCP 服务器配置
    mcp:
      server:
        type: SYNC                     # 同步模式
      client:
        stdio:
          servers-configuration: classpath:mcp-servers.json

server:
  port: 8123                          # 应用端口
  servlet:
    context-path: /api                # 路径前缀

springdoc:
  swagger-ui:
    path: /swagger-ui.html            # Swagger UI 路径

knife4j:
  enable: true                        # 启用 Knife4j
  setting:
    language: zh_cn                   # 中文界面

search-api:
  api-key: ${SEARCH_API_KEY}         # 搜索 API Key
```

## 📝 对话记忆

系统使用 `FileBasedChatMemory` 将对话历史持久化到文件系统:

```
chat-memory/
├── user_123/
│   └── conversation_001.dat
├── user_456/
│   └── conversation_001.dat
└── ...
```

每个用户的对话历史独立存储，使用 Kryo 序列化格式。

## 🗄️ 向量数据库

使用 PostgreSQL + PGVector 存储文档向量:

```sql
-- 向量存储表示例
CREATE TABLE document_vector (
    id SERIAL PRIMARY KEY,
    content TEXT,
    embedding vector(1536),
    metadata JSONB
);
```

## 🚨 常见问题

### Q: 如何获取阿里云 DashScope API Key?
A: 访问 https://dashscope.aliyun.com，注册账户并创建 API Key。

### Q: 支持哪些 LLM 模型?
A: 默认配置使用 `qwen-plus`，支持阿里云提供的所有模型:
- qwen-turbo (快速)
- qwen-plus (平衡)
- qwen-max (高质量)
- qwen-max-longcontext (超长文本)

### Q: 如何扩展工具功能?
A: 在 `tools` 目录创建新类，使用 `@Tool` 注解标记方法，然后在 `ToolRegistration` 中注册。

### Q: 知识库如何更新?
A: 通过阿里云 DashScope 控制台上传文档到 "中国旅行攻略" 知识库索引。

### Q: 支持离线运行吗?
A: 不支持。系统依赖阿里云 DashScope API 和知识库服务。

## 📊 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                    客户端应用                                  │
└────────────────────────┬────────────────────────────────────┘
                         │
                    HTTP/REST
                         │
┌─────────────────────────┴────────────────────────────────────┐
│                   Spring Boot 应用                           │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │                  Controller Layer                       │ │
│  │  • HealthController                                    │ │
│  │  • ChatController (隐含)                               │ │
│  └────────────────────┬────────────────────────────────────┘ │
│                       │                                      │
│  ┌────────────────────┴────────────────────────────────────┐ │
│  │                  Application Layer                     │ │
│  │  • TravelApp (核心业务逻辑)                             │ │
│  │  • Advisors (日志、内存管理、RAG)                       │ │
│  │  • Tools (工具调用)                                     │ │
│  └────────────────────┬────────────────────────────────────┘ │
│                       │                                      │
│  ┌────────────────────┴────────────────────────────────────┐ │
│  │                  Integration Layer                     │ │
│  │  • Spring AI ChatClient                                │ │
│  │  • DashScope API Client                                │ │
│  │  • MCP Client                                          │ │
│  │  • PGVector Store                                      │ │
│  │  • File System (Chat Memory)                           │ │
│  └────────────────────┬────────────────────────────────────┘ │
└─────────────────────────┼────────────────────────────────────┘
                         │
        ┌────────────────┼────────────────┐
        │                │                │
   ┌────┴─────┐    ┌─────┴──────┐   ┌─────┴──────┐
   │DashScope  │    │PostgreSQL  │   │   File     │
   │API        │    │+ PGVector  │   │  System    │
   │(LLM/RAG)  │    │(Vector DB) │   │ (Memory)   │
   └───────────┘    └────────────┘   └────────────┘
```

## 🧪 测试

### 运行测试
```bash
mvn test
```

### 手动测试 API
使用 Swagger UI 或 Knife4j 进行交互式测试:
```
http://localhost:8123/api/doc.html
```

## 📦 打包部署

### 构建 Docker 镜像
```bash
mvn clean package
docker build -t soulroute:latest .
docker run -p 8123:8123 \
  -e DASHSCOPE_API_KEY=your_key \
  soulroute:latest
```

### 部署到云服务
```bash
# 构建 JAR
mvn clean package

# 上传到服务器并运行
java -jar soul-route-0.0.1-SNAPSHOT.jar \
  --spring.ai.dashscope.api-key=your_key
```

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

本项目采用 MIT 许可证。

## 👨‍💻 作者

- **开发者**: Fishmoun Team
- **更新时间**: 2024-2025

## 🔗 相关资源

- [Spring AI 官方文档](https://spring.io/projects/spring-ai)
- [阿里云 DashScope](https://dashscope.aliyun.com)
- [Model Context Protocol](https://modelcontextprotocol.io)
- [Knife4j 文档](https://doc.xiaomingming.com/knife4j/)

---

**⭐ 如果这个项目对你有帮助，请给个 Star!**
