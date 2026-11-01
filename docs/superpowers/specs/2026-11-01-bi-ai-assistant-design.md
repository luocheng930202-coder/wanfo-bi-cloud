# BI 数据助理（云 API）接入设计

**日期**：2026-11-01  
**状态**：待实现（设计已确认；含 P0 知识库）  
**范围**：`Wanfo-Cloud` BI 模块（9206）+ `Wanfo-Cloud-Vue` `/bi-admin`；只读助手，不写生产仓。  
**对齐**：产品宪章「内置 AI Copilot 后置可选」；不做 DataWorks / AIOps 全自动。

## 1. 目标

在现有目录、术语、指标、任务中心、运维影响包之上，提供 **权限内** 的自然语言助手：

1. **找资产**：问「有没有某某表/指标」→ 返回有权可见的引用与跳转  
2. **解读故障 / 影响**：围绕失败任务或资产 impact-pack，生成可执行排查步骤（人审执行）  
3. **知识库问答**：优先/并行检索平台维护的 SOP、口径说明、操作手册片段，回答须可引用知识库条目  

成功标准：

- 关闭开关或未配 Key 时不可用  
- 模型上下文仅含当前用户已鉴权摘要；无权资产不出现在 prompt  
- 知识库检索结果受文档可见范围约束  
- API Key 仅存服务端；审计可追溯工具调用与会话元数据  
- 不自动重跑任务、不改权限、不写仓  

## 2. 决策摘要

| 项 | 选择 |
|---|---|
| 模型接入 | **A. 云 API**，OpenAI 兼容 Chat Completions（可换通义/DeepSeek 等） |
| 架构 | BI 模块内嵌 `AiAssistantService` + tool 白名单（否决前端直连、首版不做独立微服务） |
| 知识库 | **P0 纳入**：表存储 + **关键词/全文检索**（LIKE / 简易分词打分）；向量 embedding **延后 P1+** |
| 入口 | 工作台抽屉为主；任务失败「问助手」；知识库管理页；P1 目录「解读影响」 |
| 写操作 | 对话侧一律禁止写仓；知识库仅管理员 CRUD 文档（非生产仓） |

## 3. 调用链

```
浏览器 AssistantDrawer
  → POST /bi/ai/chat  { messages[], context? }
  → AiAssistantService
       · bi.ai.enabled + bi:ai:chat
       · 可选 context 预取摘要
       · LLM ↔ 白名单 tools（含 search_knowledge，≤ max-tool-rounds）
       · 写 dp_ai_chat_audit
  → { reply, citations[], toolTrace[] }
```

System 提示要求：涉及流程/口径/排障习惯时 **应调用** `search_knowledge`；知识库命中与资产命中一并写入 `citations`。

**Context（可选）**

| type | 预取 |
|---|---|
| `TASK_FAIL` | `taskType` + `taskId` → 任务摘要（状态、message、detailUrl） |
| `ASSET` | `assetId` → 资产短卡 + 可调 impact-pack |

## 4. 知识库（P0）

### 4.1 存什么 / 不存什么

**适合**：操作 SOP、指标口径补充说明、故障排查手册、发布检查清单、名词解释（非敏感）。  
**禁止**：生产表明细样例含 PII、连接串/密钥、未脱敏查询结果整表粘贴。

### 4.2 表结构（建议同一迁移 `V64`）

**`dp_ai_kb_doc`**

| 列 | 说明 |
|---|---|
| `id` | PK |
| `title` | 标题 |
| `tags` | 逗号或 JSON 标签（如 `pipeline,ops`） |
| `status` | `DRAFT` / `PUBLISHED` / `ARCHIVED`；检索仅 `PUBLISHED` |
| `visibility` | `ALL_AUTH`（凡有 `bi:ai:chat`）/ `ADMIN_ONLY`（仅知识库管理员可见于检索） |
| `body` | Markdown/纯文本正文（有长度上限，如 64KB） |
| `created_by` / `updated_by` / 时间戳 | 审计 |

**`dp_ai_kb_chunk`**（入库时按标题/二级标题或固定字数切分）

| 列 | 说明 |
|---|---|
| `id`, `doc_id` | FK |
| `chunk_index` | 序 |
| `content` | 片段文本（如 ≤ 1500 字） |
| `content_norm` | 可选：小写/去空白，便于 LIKE |

P0 **不建**向量列；P1 可加 `embedding` BLOB/JSON 或旁路表。

### 4.3 管理 API

| 方法 | 路径 | 权限 |
|---|---|---|
| GET | `/bi/ai/kb/docs` | `bi:ai:kb:list`（或 `bi:ai:kb:admin`） |
| POST | `/bi/ai/kb/docs` | `bi:ai:kb:admin` |
| PUT | `/bi/ai/kb/docs/{id}` | `bi:ai:kb:admin` |
| POST | `/bi/ai/kb/docs/{id}/publish` | `bi:ai:kb:admin` |
| DELETE | `/bi/ai/kb/docs/{id}` | `bi:ai:kb:admin`（软删或 ARCHIVED） |

保存/发布时同步重切 `dp_ai_kb_chunk`。

### 4.4 检索（P0）

`search_knowledge(query, limit)`：

1. 仅 `PUBLISHED`，并按当前用户过滤 `visibility`  
2. 对 `title`/`tags`/`content` 做关键词分词（空格/简单切词）+ `LIKE %term%` 打分（标题命中权重大）  
3. 返回 Top-K 片段：`docId, title, chunk, score`  
4. 受 `bi.ai.max-list-rows` / `max-tool-result-chars` 约束  

## 5. API（对话）

### 5.1 `POST /bi/ai/chat`

**权限**：`bi:ai:chat`  

**请求（示意）**

```json
{
  "sessionId": "optional-uuid",
  "messages": [{ "role": "user", "content": "管道失败了一般怎么排查" }],
  "context": { "type": "TASK_FAIL", "taskType": "PIPELINE", "taskId": 123 }
}
```

**响应（示意）**

```json
{
  "reply": "…",
  "citations": [
    { "kind": "KB", "id": 12, "title": "管道失败排查 SOP", "path": "/bi-admin/ai-kb?doc=12" },
    { "kind": "TASK", "id": 123, "title": "PIPELINE#123", "path": "/bi-admin/task-center" }
  ],
  "toolTrace": [
    { "name": "search_knowledge", "ok": true, "hitCount": 2 },
    { "name": "get_task_run", "ok": true, "hitCount": 1 }
  ],
  "usage": { "promptTokens": 0, "completionTokens": 0 }
}
```

### 5.2 配置（默认关闭）

```yaml
bi.ai.enabled: false
bi.ai.base-url: https://api.openai.com/v1
bi.ai.api-key: ${WANFO_BI_AI_API_KEY:}
bi.ai.model: gpt-4o-mini
bi.ai.max-tool-rounds: 4
bi.ai.audit-store-prompt: false
bi.ai.max-list-rows: 20
bi.ai.max-tool-result-chars: 8000
bi.ai.kb.chunk-size: 1200
bi.ai.kb.max-body-chars: 65536
```

## 6. Tool 白名单（P0）

| Tool | 说明 | 底层 |
|---|---|---|
| `search_knowledge` | 知识库片段检索 | `AiKnowledgeService` |
| `search_catalog` | 关键词搜资产 | Catalog / assets（权限过滤） |
| `search_glossary` | 术语 | GlossaryTermService |
| `search_metrics` | 指标 | MetricDefinitionService |
| `get_task_run` | 单任务摘要 | TaskCenterService |
| `get_impact_pack` | 影响包（沿用脱敏） | 现有 impact-pack 逻辑 |
| `list_failed_tasks` | 最近失败（有界） | tasks `status=FAILED` |

**禁止**：任意 HTTP、自由 SQL、装载/管道提交、override、密钥轮换、DDL。

## 7. Prompt 与模型行为

- System：仅依据 tool 结果作答；流程/口径类问题优先 `search_knowledge`；无命中则说明未找到或无权限；给出站内路径；不编造表名/行数  
- Temperature ≤ 0.2；`max_tokens` 有上限  
- 多轮 tool ≤ `max-tool-rounds`  

## 8. 审计（`V64__dp_ai_assistant.sql`）

同迁移包含：

1. `dp_ai_chat_audit`：`user_id, session_id, scene, model, tokens, tools_json, reply_digest, prompt_hash, created_at`  
2. `dp_ai_kb_doc` / `dp_ai_kb_chunk`  

`audit-store-prompt` 默认 false。知识库管理写操作走常规服务审计（更新人/时间）；可选 RuoYi 操作日志。

## 9. 前端

| 页面/组件 | 说明 |
|---|---|
| `views/bi/ai/AssistantDrawer.vue` | 对话抽屉；展示 KB/资产 citations |
| `views/bi/ai-kb/index.vue` | 知识库管理：列表、编辑 Markdown、发布/归档 |
| `api/bi/ai.ts` | chat + kb CRUD |

- 工作台打开助理；任务中心失败行「问助手」  
- 导航：数据中心「治理与运维」下增加「助理知识库」（需 `bi:ai:kb:admin` 或 list）  
- P1：目录解读影响、SSE、向量检索  
- 样式跟随 `data-bi-theme`  

## 10. 安全

1. 对话与检索均按当前用户权限；`ADMIN_ONLY` 文档不对普通聊天用户召回  
2. API Key 不下发前端  
3. 知识库正文长度与上传频率可配上限，防把整库当对象存储滥用  
4. 助手不能提升权限或绕过 access-request  

## 11. 落地批次

| 批次 | 内容 |
|---|---|
| **P0** | 配置 + chat + **7 tools（含 search_knowledge）** + KB 表/管理 API/管理页 + 审计 + 工作台抽屉 + 任务失败入口 |
| **P1** | 目录解读影响、引用跳转、SSE、embedding 向量检索 |
| **P2** | 图表/指标草稿建议（只读草稿） |
| **不做** | 前端直连 LLM、自动重跑/审批、独立 AI 微服务（除非规模触发） |

## 12. 验收（P0）

- [ ] `enabled=false` 入口不可用  
- [ ] 发布一篇含「管道失败」的 SOP 后，提问能引用该 KB citation  
- [ ] `DRAFT` / `ADMIN_ONLY` 对普通用户不可被聊到  
- [ ] 有权资产检索与无权隔离仍成立  
- [ ] 失败任务解读含真实 status/message + 链接  
- [ ] 审计表有记录；无 Key 泄露  
- [ ] SYSTEM-DESIGN 增加「数据助理 / 知识库」短述 + 变更日志（实现时） |

## 13. 预期代码入口

| 侧 | 路径 |
|---|---|
| 配置 | `bi.ai.*` |
| API | `AiAssistantController`、`AiKnowledgeController` |
| 服务 | `AiAssistantService`、`AiKnowledgeService` + `ai/tool/*` |
| 迁移 | `V64__dp_ai_assistant.sql`（audit + kb） |
| 前端 | `AssistantDrawer.vue`、`ai-kb/index.vue`、`api/bi/ai.ts` |

## 14. 文档

实现后更新 `docs/SYSTEM-DESIGN.md` 对应章节与 §16.4；本 spec 状态改为「已实现」。

---

**下一步**：确认本修订后，编写 `docs/superpowers/plans/2026-11-01-bi-ai-assistant.md` 实现计划。
