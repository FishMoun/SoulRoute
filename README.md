# SoulRoute 后端

SoulRoute 是一个基于 `Spring Boot + Spring AI + PGVector + RAG + Tool Calling + MCP` 的旅行规划智能体后端。系统面向出行规划场景，提供多轮对话、旅行攻略知识库检索、ReAct 工具调用、PDF 攻略生成、用户偏好记忆、会话持久化、Skill 化提示词和自进化沉淀能力。

## 核心能力

- **ReAct 智能体**：按 `Think -> Act -> Observe -> Finish` 流程执行任务，支持实时 SSE 步骤输出。
- **RAG 知识库**：解析本地 `docx` 旅行攻略，切分 chunk，写入 PGVector，支持混合检索。
- **查询理解增强**：结合规则查询重写、多查询扩展、实体扩展和 LLM Slot Extraction，提高检索命中率。
- **Tool Calling**：集成旅行知识库检索、网页搜索、网页抓取、文件操作、PDF 生成、资源下载等工具。
- **MCP 扩展**：保留 Spring AI MCP Client/Server 接入能力，支持外部工具扩展。
- **用户体系**：支持注册、登录、轻量 Bearer Token。
- **用户偏好记忆**：支持手动保存旅行偏好，也能从对话上下文自动总结长期偏好。
- **会话持久化**：登录用户使用 PostgreSQL 会话表，匿名用户兼容 Kryo 文件记忆。
- **Skill 自进化**：记录 ReAct 轨迹，识别失败模式，生成 Skill Proposal，评估后激活新版本，并支持回滚。

## 技术栈

- Java 21
- Spring Boot 3.4.4
- Spring AI 1.0.0
- Spring AI Alibaba 1.0.0.2
- DashScope / Qwen Plus
- PostgreSQL + PGVector
- Flyway
- Kryo
- iText
- Jsoup
- Knife4j / Springdoc OpenAPI

## 项目结构

```text
src/main/java/com/fishmoun/soulroute
├── agent/          # ReAct Agent、步骤、状态和提示词拼装
├── app/            # TravelApp 传统 ChatClient 能力
├── auth/           # 注册、登录、Token 和当前用户解析
├── chatmemory/     # Kryo 文件会话记忆
├── config/         # CORS、RAG、工具注册等配置
├── controller/     # REST/SSE 接口
├── conversation/   # 用户级会话历史
├── evolution/      # ReAct 轨迹、自进化和 Skill 评估
├── preference/     # 用户旅行偏好与偏好学习
├── rag/            # 文档 ETL、PGVector、混合检索、查询理解
├── skill/          # Skill、版本、激活和回滚
└── tools/          # Web、PDF、文件、知识库等工具
```

## 数据库

主要表：

- `app_users`：用户注册登录。
- `user_preferences`：用户旅行偏好。
- `conversations` / `conversation_messages`：用户级历史会话。
- `travel_document_chunks`：RAG chunk、metadata 和 embedding。
- `travel_skills` / `travel_skill_versions`：Skill 主体和版本。
- `skill_evolution_events`：Skill 沉淀事件。
- `react_runs` / `react_run_steps`：ReAct 运行轨迹。
- `skill_evaluation_runs`：Skill 候选版本评估。
- `skill_activation_history`：Skill 激活和回滚历史。

详细说明见项目文档 `DATABASE_DESIGN.md`。

## 环境要求

- JDK 21
- Docker Desktop 或本地 PostgreSQL + pgvector
- DashScope API Key
- Maven Wrapper 已包含在项目内

## 启动 PostgreSQL + PGVector

```powershell
docker compose -f docker-compose.pgvector.yml up -d
```

默认连接配置：

```yaml
POSTGRES_URL=jdbc:postgresql://localhost:5432/soulroute
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres
```

## 环境变量

建议通过环境变量传入敏感配置：

```powershell
$env:DASHSCOPE_API_KEY="your_dashscope_key"
$env:SOULROUTE_TOKEN_SECRET="your_random_secret"
```

可选配置：

```powershell
$env:SOULROUTE_TOKEN_TTL_HOURS="168"
```

## 运行后端

普通启动：

```powershell
.\mvnw.cmd spring-boot:run
```

如果本地 MCP 初始化不稳定，可以临时禁用 MCP Client：

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.ai.mcp.client.enabled=false"
```

健康检查：

```powershell
Invoke-RestMethod http://localhost:8123/api/health
```

## 构建与测试

```powershell
.\mvnw.cmd -q -DskipTests package
```

RAG 召回评估测试：

```powershell
.\mvnw.cmd -Dtest=RagRecallEvaluationTest test
```

评估报告输出到：

```text
target/rag-recall-evaluation.md
```

## RAG 配置

核心配置位于 `src/main/resources/application.yml`：

```yaml
soulroute:
  rag:
    documents-location: classpath*:com/fishmoun/soulroute/data/**/*.docx
    index-on-startup: false
    top-k: 5
    similarity-threshold: 0.65
    hybrid:
      vector-top-k: 12
      metadata-top-k: 12
      vector-weight: 0.7
      metadata-weight: 0.3
    query-expansion:
      enabled: true
      max-vector-queries: 6
    query-understanding:
      enabled: true
```

说明：

- `index-on-startup=false` 避免每次启动重复 embedding。
- 手动重建索引时，可临时改为 `true` 或增加专用 reindex 接口。
- 查询理解会额外调用一次大模型，能提升模糊表达理解能力，但会增加延迟和模型调用成本。

## 主要 API

基础路径：

```text
http://localhost:8123/api
```

### 认证

```http
POST /auth/register
POST /auth/login
```

### 用户偏好

```http
GET /users/me/preferences
PUT /users/me/preferences
```

### ReAct 对话

```http
POST /travel/react
POST /travel/react/stream
```

`/travel/react/stream` 使用 SSE 返回：

- `status`：智能体状态
- `step`：工具调用步骤
- `final`：最终结果
- `error`：错误信息

### 历史会话

```http
GET /travel/conversations
GET /travel/conversations/{chatId}
DELETE /travel/conversations/{chatId}
```

### PDF 下载

```http
GET /travel/files/pdf/{fileName}
```

### Skill 管理

```http
GET /travel/skills
GET /travel/skills/{skillId}/versions
POST /travel/skills/evolve
POST /travel/skills/{skillId}/versions/{versionId}/activate
POST /travel/skills/{skillId}/rollback/{versionId}
```

## Skill 自进化流程

1. ReAct 执行完成后保存运行轨迹。
2. 系统识别失败模式，如 PDF 失败、检索不准、用户纠错、工具失败。
3. 根据失败类型生成 Skill Proposal。
4. 新版本先写入 `DRAFT`。
5. 读取历史同类失败任务做轻量评估。
6. 达标后自动激活为 `ACTIVE`，旧版本变为 `INACTIVE`。
7. 所有版本保留，可通过接口回滚。

当前评估是工程启发式评估，后续可升级为离线测试集回放或 LLM Judge。

## 本地模拟测试

项目顶层提供了模拟脚本：

```powershell
..\simulate_agent_usage.ps1
```

该脚本会模拟用户注册、偏好填写、多轮 ReAct 对话、Skill evolve/activate/rollback，并生成测试报告。

## 注意事项

- 不要把真实 API Key 写入仓库，生产环境请使用环境变量。
- `chat-memory/` 下的 Kryo 文件属于运行数据，一般不应提交。
- `tmp/`、`run-logs/`、`target/`、前端 `dist/` 都属于生成物。
- 如果启用 MCP Client 时启动超时，可以先用 `--spring.ai.mcp.client.enabled=false` 启动核心能力。
