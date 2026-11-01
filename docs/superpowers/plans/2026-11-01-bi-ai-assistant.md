# BI 数据助理 + P0 知识库 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 9206 BI 模块落地只读云 API 数据助理（找资产 / 解读失败任务 / 知识库问答），含 KB 管理与工作台入口。

**Architecture:** `AiAssistantService` 调 OpenAI 兼容 Chat Completions，经白名单 tools 访问本进程 Catalog/Metric/Glossary/Task/Impact/KB 服务（带当前用户 SecurityContext）；知识库存 `dp_ai_kb_*`，P0 关键词检索；审计写 `dp_ai_chat_audit`。

**Tech Stack:** Java 17 / Spring Boot（ruoyi-bi）、Flyway BI、MyBatis、Vue 3 + Element Plus、WebClient/RestClient 调 LLM。

**Spec:** `docs/superpowers/specs/2026-11-01-bi-ai-assistant-design.md`

## Global Constraints

- 默认 `bi.ai.enabled=false`；无 Key 不可聊天
- 对话只读：无写仓 / 重跑 / 审批 / 自由 SQL tool
- 知识库 P0 仅关键词检索；向量延后
- API Key 仅服务端；`audit-store-prompt` 默认 false
- 权限：`bi:ai:chat`、`bi:ai:kb:admin`（list 可与 admin 同权或只读 list）
- Commit 步骤仅当用户明确要求提交时执行
- 实现后必须更新 `docs/SYSTEM-DESIGN.md`（工作区规则）

## File map

| 文件 | 职责 |
|---|---|
| `.../db/migration/bi/V64__dp_ai_assistant.sql` | audit + kb 表 |
| `.../config/BiProperties.java`（内嵌 `Ai`） | `bi.ai.*` |
| `.../ai/OpenAiCompatibleClient.java` | chat.completions HTTP |
| `.../service/AiKnowledgeService.java` | 文档 CRUD、切块、search |
| `.../service/AiAssistantService.java` | tool 循环 + 审计 |
| `.../controller/AiAssistantController.java` | `POST /bi/ai/chat` |
| `.../controller/AiKnowledgeController.java` | `/bi/ai/kb/docs` |
| `.../mapper/*` + xml | KB / audit 持久化 |
| `Wanfo-Cloud-Vue/src/api/bi/ai.ts` | 前端 API |
| `.../views/bi/ai/AssistantDrawer.vue` | 对话抽屉 |
| `.../views/bi/ai-kb/index.vue` | 知识库管理 |
| `BiWorkspaceLayout.vue` + `router/index.ts` | 导航与路由 |
| `docs/SYSTEM-DESIGN.md` | 设计原则短章 |

---

### Task 1: Flyway V64 表结构

**Files:**
- Create: `Wanfo-Cloud/ruoyi-modules/ruoyi-bi/src/main/resources/db/migration/bi/V64__dp_ai_assistant.sql`

**Interfaces:**
- Produces: tables `dp_ai_chat_audit`, `dp_ai_kb_doc`, `dp_ai_kb_chunk`

- [ ] **Step 1: 写入迁移 SQL**

```sql
CREATE TABLE dp_ai_chat_audit (
    id bigint NOT NULL AUTO_INCREMENT,
    user_id bigint DEFAULT NULL,
    session_id varchar(64) DEFAULT NULL,
    scene varchar(32) NOT NULL DEFAULT 'WORKBENCH',
    model varchar(128) NOT NULL,
    prompt_tokens int DEFAULT NULL,
    completion_tokens int DEFAULT NULL,
    tools_json json DEFAULT NULL,
    reply_digest varchar(512) DEFAULT NULL,
    prompt_hash varchar(64) DEFAULT NULL,
    created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_dp_ai_chat_audit_user (user_id, created_at),
    KEY idx_dp_ai_chat_audit_scene (scene, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI assistant chat audit';

CREATE TABLE dp_ai_kb_doc (
    id bigint NOT NULL AUTO_INCREMENT,
    title varchar(200) NOT NULL,
    tags varchar(500) DEFAULT NULL,
    status varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT|PUBLISHED|ARCHIVED',
    visibility varchar(16) NOT NULL DEFAULT 'ALL_AUTH' COMMENT 'ALL_AUTH|ADMIN_ONLY',
    body mediumtext NOT NULL,
    created_by varchar(64) DEFAULT NULL,
    updated_by varchar(64) DEFAULT NULL,
    create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_dp_ai_kb_doc_status (status, visibility)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI knowledge documents';

CREATE TABLE dp_ai_kb_chunk (
    id bigint NOT NULL AUTO_INCREMENT,
    doc_id bigint NOT NULL,
    chunk_index int NOT NULL,
    content varchar(4000) NOT NULL,
    content_norm varchar(4000) DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_dp_ai_kb_chunk_doc (doc_id, chunk_index),
    CONSTRAINT fk_dp_ai_kb_chunk_doc FOREIGN KEY (doc_id) REFERENCES dp_ai_kb_doc (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI knowledge chunks';
```

- [ ] **Step 2: 确认版本号**

确认目录下无其它 `V64__*`；若有冲突则改用下一号并同步改本计划全文 `V64` 引用。

- [ ] **Step 3: Commit（仅用户要求时）**

```bash
git add Wanfo-Cloud/ruoyi-modules/ruoyi-bi/src/main/resources/db/migration/bi/V64__dp_ai_assistant.sql
git commit -m "feat(bi): add V64 AI assistant audit and knowledge tables"
```

---

### Task 2: `BiProperties.Ai` + OpenAI 兼容客户端

**Files:**
- Modify: `Wanfo-Cloud/ruoyi-modules/ruoyi-bi/src/main/java/com/wanfo/bi/config/BiProperties.java`
- Create: `Wanfo-Cloud/ruoyi-modules/ruoyi-bi/src/main/java/com/wanfo/bi/ai/OpenAiCompatibleClient.java`
- Create: `Wanfo-Cloud/ruoyi-modules/ruoyi-bi/src/main/java/com/wanfo/bi/ai/AiChatRequest.java`（records/DTO，可放同包）
- Modify: BI `application.yml`（或 `application-bi.yml` 若存在）增加默认 `bi.ai` 段

**Interfaces:**
- Produces:
  - `BiProperties.getAi(): Ai` with `enabled, baseUrl, apiKey, model, maxToolRounds, auditStorePrompt, maxListRows, maxToolResultChars, kbChunkSize, kbMaxBodyChars`
  - `OpenAiCompatibleClient.chat(List<Map<String,Object>> messages, List<Map<String,Object>> tools) -> Map`（含 `choices`, `usage`, 可能的 `tool_calls`）

- [ ] **Step 1: 在 `BiProperties` 增加内嵌类 `Ai` 与 getter/setter**（字段默认值对齐 spec）

```java
private Ai ai = new Ai();
public Ai getAi() { return ai; }
public void setAi(Ai ai) { if (ai != null) this.ai = ai; }

public static class Ai {
    private boolean enabled = false;
    private String baseUrl = "https://api.openai.com/v1";
    private String apiKey = "";
    private String model = "gpt-4o-mini";
    private int maxToolRounds = 4;
    private boolean auditStorePrompt = false;
    private int maxListRows = 20;
    private int maxToolResultChars = 8000;
    private int kbChunkSize = 1200;
    private int kbMaxBodyChars = 65536;
    // getters/setters with clamp where sensible
}
```

- [ ] **Step 2: 实现 `OpenAiCompatibleClient`**

使用 `RestClient` 或已有 WebClient：`POST {baseUrl}/chat/completions`，Header `Authorization: Bearer {apiKey}`，body：`model`, `messages`, `tools`（若非空）, `temperature: 0.2`。  
`enabled=false` 或 `apiKey` 空白时抛出明确业务异常（如 `ServiceException` / 项目惯用类型），文案：`AI assistant is disabled` / `AI API key not configured`。

- [ ] **Step 3: yml 默认段**

```yaml
bi:
  ai:
    enabled: false
    base-url: https://api.openai.com/v1
    api-key: ${WANFO_BI_AI_API_KEY:}
    model: gpt-4o-mini
    max-tool-rounds: 4
    audit-store-prompt: false
```

- [ ] **Step 4: Commit（仅用户要求时）**

---

### Task 3: 知识库 Service + API + 检索单测

**Files:**
- Create mapper/domain/xml for `dp_ai_kb_doc`, `dp_ai_kb_chunk`
- Create: `.../service/AiKnowledgeService.java`
- Create: `.../controller/AiKnowledgeController.java`
- Create: `.../src/test/java/com/wanfo/bi/service/AiKnowledgeSearchTest.java`

**Interfaces:**
- Produces:
  - `listDocs()`, `get(id)`, `create/update`, `publish(id)`, `archive(id)`
  - `rechunk(docId)` — 按 `kbChunkSize` 切分写入 chunks
  - `search(String query, boolean adminCaller): List<Hit>` — 仅 PUBLISHED；非 admin 排除 `ADMIN_ONLY`
  - Hit: `docId, title, chunk, score`

- [ ] **Step 1: 写失败单测（纯打分函数）**

在 `AiKnowledgeService`（或 package-visible `AiKnowledgeScorer`）实现：

```java
static int score(String query, String title, String tags, String content)
```

规则示例：query 按空白拆 term；title 命中 +10/term，tags +5，content +2；无 term 返回 0。

测试：

```java
@Test
void titleHitRanksHigherThanBodyOnly() {
    int a = AiKnowledgeScorer.score("管道 失败", "管道失败排查", "", "其他");
    int b = AiKnowledgeScorer.score("管道 失败", "其它文档", "", "管道失败说明很长");
    assertTrue(a > b);
}
```

先跑确认可红（类不存在）再实现。

- [ ] **Step 2: 实现 Service + Mapper**

- create/update 校验 `body.length() <= kbMaxBodyChars`  
- publish：status=PUBLISHED 并 `rechunk`  
- search：SQL 取候选（`status='PUBLISHED'` + visibility 过滤）后在内存打分排序，截断 `maxListRows`

- [ ] **Step 3: Controller**

```java
@RequestMapping("/bi/ai/kb/docs")
// GET /  @BiPermission("bi:ai:kb:admin")
// POST / PUT /{id} POST /{id}/publish POST /{id}/archive  @BiPermission("bi:ai:kb:admin")
```

首版 list/admin 可同用 `bi:ai:kb:admin`（降低菜单复杂度）。

- [ ] **Step 4: 跑单测**

```bash
cd Wanfo-Cloud/ruoyi-modules/ruoyi-bi
mvn -Dtest=AiKnowledgeSearchTest test
```

Expected: PASS

- [ ] **Step 5: Commit（仅用户要求时）**

---

### Task 4: Assistant tools + chat API + 审计

**Files:**
- Create: `.../ai/tool/AiTool.java`（name + schema + execute）
- Create: tools: `SearchKnowledgeTool`, `SearchCatalogTool`, `SearchGlossaryTool`, `SearchMetricsTool`, `GetTaskRunTool`, `GetImpactPackTool`, `ListFailedTasksTool`
- Create: `.../service/AiAssistantService.java`
- Create: `.../controller/AiAssistantController.java`
- Create mapper insert for `dp_ai_chat_audit`

**Interfaces:**
- Consumes: `OpenAiCompatibleClient`, `AiKnowledgeService`, existing Catalog/Metric/Glossary/Task/Impact services
- Produces: `AiAssistantService.chat(ChatCmd) -> ChatResult`
- `POST /bi/ai/chat` `@BiPermission("bi:ai:chat")`

- [ ] **Step 1: 定义 ChatCmd / ChatResult**

```java
public record ChatCmd(String sessionId, List<Msg> messages, Context context) {}
public record Context(String type, String taskType, Long taskId, Long assetId) {}
public record ChatResult(String reply, List<Citation> citations, List<ToolTrace> toolTrace, Usage usage) {}
```

- [ ] **Step 2: 实现各 Tool.execute**

统一返回 `Map`/`JSON` 字符串，长度截断 `maxToolResultChars`。  
`SearchCatalogTool` 等必须走现有带权限的 list/get；无权限 → 空列表。

- [ ] **Step 3: `AiAssistantService.chat` 主循环**

1. 校验 `ai.enabled` + key  
2. 若 context 为 TASK_FAIL / ASSET，预取摘要塞进 messages（role=system 或 tool 结果模拟）  
3. System 文案：只根据 tools 回答；流程问题先 `search_knowledge`；不编造  
4. for i in 1..maxToolRounds: 调 LLM；若 tool_calls 则执行并 append；否则 break  
5. 组装 citations（从 tool 结果收集）  
6. insert audit（tools_json、tokens、reply_digest；prompt 仅 hash，除非 auditStorePrompt）

- [ ] **Step 4: Controller**

```java
@PostMapping("/bi/ai/chat")
@BiPermission("bi:ai:chat")
public Map<String, Object> chat(@RequestBody ...) { ... }
```

- [ ] **Step 5: 编译**

```bash
cd Wanfo-Cloud/ruoyi-modules/ruoyi-bi
mvn -DskipTests compile
```

Expected: BUILD SUCCESS（若缺依赖按项目现有 HTTP 客户端补）

- [ ] **Step 6: Commit（仅用户要求时）**

---

### Task 5: 前端 API + 对话抽屉 + 工作台/任务入口

**Files:**
- Create: `Wanfo-Cloud-Vue/src/api/bi/ai.ts`
- Create: `Wanfo-Cloud-Vue/src/views/bi/ai/AssistantDrawer.vue`
- Modify: `Wanfo-Cloud-Vue/src/views/bi/workbench/index.vue`（打开抽屉按钮）
- Modify: `Wanfo-Cloud-Vue/src/views/bi/task-center/index.vue`（失败行「问助手」）
- Optional: provide/inject 或小型 event bus / pinia 控制抽屉；可用工作台内 `ref` + 全局 `window` 自定义事件 `bi-ai-open` 降低耦合

**Interfaces:**
- `chatAi(body) => request({ url: '/bi/ai/chat', method: 'post', data })`
- Drawer props/events: `open(context?)`

- [ ] **Step 1: 写 `api/bi/ai.ts`**

含 `chatAi`, `listKbDocs`, `saveKbDoc`, `publishKbDoc`, `archiveKbDoc`。

- [ ] **Step 2: `AssistantDrawer.vue`**

- el-drawer；消息列表；输入框发送  
- 展示 `citations`（KB / TASK / METRIC 等）为可点击 router-link  
- `checkPermi(['bi:ai:chat'])` 与后端 403 处理  
- 监听 `bi-ai-open` CustomEvent 打开并带 detail.context  

- [ ] **Step 3: 挂载抽屉**

在 `BiWorkspaceLayout.vue` 或 `workbench` 挂载 `<AssistantDrawer />`（推荐 Layout，使任务中心也能用）：

```vue
<AssistantDrawer />
```

工作台按钮：

```ts
window.dispatchEvent(new CustomEvent('bi-ai-open', { detail: {} }))
```

任务中心失败行：

```ts
window.dispatchEvent(new CustomEvent('bi-ai-open', {
  detail: { context: { type: 'TASK_FAIL', taskType: row.type, taskId: row.id } }
}))
```

- [ ] **Step 4: 手工验收（需 enabled + Key）**

无 Key 时：点入口应看到后端明确错误提示。  
有 Key：发「你好」应返回（可无 KB）。

- [ ] **Step 5: Commit（仅用户要求时）**

---

### Task 6: 知识库管理页 + 路由 + 导航

**Files:**
- Create: `Wanfo-Cloud-Vue/src/views/bi/ai-kb/index.vue`
- Modify: `Wanfo-Cloud-Vue/src/router/index.ts`（constant `/bi-admin/ai-kb`）
- Modify: `Wanfo-Cloud-Vue/src/layout/components/BiWorkspaceLayout.vue`（platform 治理组增加「助理知识库」，permission `bi:ai:kb:admin`）
- Modify: `deploy/ruoyi-menu.sql` 或文档说明手工赋权（按仓库惯例追加菜单/权限字）

- [ ] **Step 1: 管理页**

表格：标题、状态、可见性、标签、操作（编辑/发布/归档）。  
编辑抽屉：title、tags、visibility、body（textarea）。  
保存后可发布。

- [ ] **Step 2: 路由**

```ts
{ path: 'ai-kb', name: 'BiAiKb', component: () => import('@/views/bi/ai-kb/index.vue'),
  meta: { title: '助理知识库', noCache: true, activeMenu: '/bi-admin/ai-kb' } }
```

- [ ] **Step 3: 侧栏**

在 `platformGroups` 的 `govern` items 增加：

```ts
{ path: '/bi-admin/ai-kb', label: '助理知识库', icon: Reading, permission: 'bi:ai:kb:admin' }
```

- [ ] **Step 4: 权限种子**

在 `ruoyi-menu.sql`（或等价）增加权限字 `bi:ai:chat`、`bi:ai:kb:admin` 说明；若项目用 SQL 插菜单则补一行。实现者按现有 backup-drills 菜单写法复制。

- [ ] **Step 5: 端到端验收清单**

1. 管理员发布标题含「管道失败」的 SOP  
2. 聊天提问「管道失败怎么排查」→ citation kind=KB  
3. DRAFT 文档不可被聊到  
4. ADMIN_ONLY + 非 admin 用户（若可测）不可召回  

- [ ] **Step 6: Commit（仅用户要求时）**

---

### Task 7: SYSTEM-DESIGN + spec 状态

**Files:**
- Modify: `docs/SYSTEM-DESIGN.md`（新小节「数据助理」或挂在 §15 后；§16.3 路由；§16.4 日志）
- Modify: `docs/superpowers/specs/2026-11-01-bi-ai-assistant-design.md` 状态 → 已实现（本 Task 完成且 P0 验收通过后）

- [ ] **Step 1: SYSTEM-DESIGN 短章**

写明：云 API、tool 白名单、KB 关键词检索、默认关闭、审计表 V64、禁止写仓。

- [ ] **Step 2: §16.4 一行**

`| 2026-11-01 | 数据助理：云 API + 知识库关键词检索（V64） |`

- [ ] **Step 3: Commit（仅用户要求时）**

---

## Spec coverage

| Spec | Task |
|---|---|
| 云 API + enabled 开关 | 2, 4 |
| chat + 7 tools（含 search_knowledge） | 4 |
| KB 表/管理/关键词检索 | 1, 3, 6 |
| 审计 | 1, 4 |
| 工作台 + 任务入口 | 5 |
| SYSTEM-DESIGN | 7 |
| 向量 / SSE / 目录解读 | **不在本计划**（P1） |

## Self-check

- 无 TBD；V64 若冲突则升版号并全文替换  
- 权限字统一 `bi:ai:chat` / `bi:ai:kb:admin`  
- P0 明确无 embedding
