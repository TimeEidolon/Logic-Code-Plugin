---
name: typescript-project-decomposer
description: Produces **two docs only**: `docs/PROJECT_OVERVIEW.md` (**project baseline + ONE system-wide map**) and `docs/PROJECT_FEATURE_DETAILS.md` split into **two dimensions — Page (`src/pages` packages) and Feature (cross-page capabilities)** with graphs/state/contracts. No `PROJECT_FEATURES.md`. No overview/narrative inside the details file. Use for decomposition, diagrams, HelpCenter-style repos, **full inventory without page-by-page prompting**.
---

# Project Decomposer (项目分解与流转图)

## Goals

**默认交付两套文件（同一轮 skill 执行内写齐）**，让读者 **先总览、后下钻**：

| 文件 | 读者目的 | **允许包含** | **禁止**（挪到详情） |
|------|----------|----------------|----------------------|
| `docs/PROJECT_OVERVIEW.md` | ~5 分钟建立项目心智 | 一句话定位、技术栈、`src/` 地图、如何运行、`Unknowns`、**恰好一张**全景 `flowchart`（Pages + shared + APIs）、**两段索引表**指向详情：① **Page → `#detail-NN`** ② **功能 → `#feat-*`** | Page/功能相关的 **多张**示意、逐包 **stateDiagram**、长 **`ts` 契约**（一律在详情） |
| `docs/PROJECT_FEATURE_DETAILS.md` | 按 **Page** 或按 **功能** 下钻 | **固定两篇块、顺序无所谓但标题要有**：`<a id="page-dimension-marker"></a>` + `## Page 维度`（D1 表 + `<a id="detail-01"></a>`… **每包** 一节：协作 + 数据流 + state + 契约 + 可选 sequence）；`<a id="feature-dimension-marker"></a>` + `## 功能维度`（**索引表** + `<a id="feat-ai"></a>`… **每能力** 一节：端到端图 + state + 契约 + **回链** `#detail-NN`，避免与 Page 块重复粘贴时再摘一层抽象） | **禁止**：项目背景/ readme 式总览复述、整仓鸟瞰 Mermaid（**仅允许**写在 `PROJECT_OVERVIEW.md`）、任何第三份主线文档（**不要** `PROJECT_FEATURES.md`） |

**其它约定**
- Mermaid **只内嵌 markdown**（默认不建 `docs/diagrams/*.mmd`）。
- **稳定锚点**：Page 小节用 `detail-01`…；功能小节用语义化 **`feat-<slug>`**；维度入口用 **`page-dimension-marker` / `feature-dimension-marker`**。总览 **只链接这些 id**。
- 可选：`docs/project-map.canvas.tsx`（读 `~/.cursor/skills-cursor/canvas/SKILL.md`）。

**非 HelpCenter 仓库**：仍产 **相同两名文件**；`PROJECT_FEATURE_DETAILS.md` 按 **域/模块/特性** 分 §，而不是按 `src/pages` 时，在总览中说明分组依据即可。

## 分解维度（不止「按 page 扫一遍」）

**Page 维度**回答：仓库里有哪些 **可独立构建/路由挂载的入口包**（`src/pages/<pkg>/`）。这是 **Step A1** 的 Glob 矩阵，用于不漏入口。

还需要下面维度，避免「只有页面表、没有业务地图」：

| 维度 | 回答的问题 | 典型扫法 |
|------|------------|----------|
| **D1 — 部署/路由入口（page）** | 有哪些页面包、SSR/CSR 形态？ | `src/pages/**` 下 `App.tsx`、`entry-server.tsx`、`entry.{tsx,ts,js}` Glob → 入口表 |
| **D2 — 横切/共享能力（technical capabilities）** | 哪些 UI/工具被多页复用？ | `src/common/**` 顶层目录 Glob；读 `package.json` workspace；搜 `@/` 别名指向 |
| **D3 — 业务功能点（product features）** | 用户能完成哪些事、跨哪些页？ | **归纳**：从 page 的 `modules/*` + store + `src/common` 抽 **能力标签**（如「AI 问答」「目录树」「搜索」「SSR 首页」「i18n」「埋点」），写入 **功能点 × 承载** 表：功能名 → 相关 § / 页面目录 → `common` 或 `api` 路径 |
| **D4 — 外部与生成契约** | 数据从哪来？ | `src/.cloudapi`、OpenAPI 生成物；各页 `api.ts` / `api.js`；环境 `window.g_*` / SSR `injection`（在契约块里用注释说明来源） |

**执行顺序**：先 **D1**（进 **Page 维度** 矩阵与小节）→ 再 **D2+D3**（进 **功能维度** 索引与各 `feat-*` 小节，并指向相关 `detail-*`）→ **不要**把 D1/D3 揉成「只有一种目录」的单章。

## HelpCenter：双文件一键工作流（skill 触发后按顺序执行）

当仓库为 HelpCenter（存在 `src/pages/` 与 `src/common/` 等典型结构），或用户要求「全量入口 / 不要逐页问要不要补某页」时，**必须**按序更新 **`PROJECT_OVERVIEW.md` + `PROJECT_FEATURE_DETAILS.md`**，**不要**用「要不要补第 N 页」阻塞 **A1–B1**。

**输出物名称固定**：`docs/PROJECT_OVERVIEW.md`、`docs/PROJECT_FEATURE_DETAILS.md`。

### Step A1 — 全量 **页面** 入口扫描（固定 Glob）

在 `src/pages` 下分别 Glob，合并去重得到 **每一个子目录**：

| 模式 | 含义 |
|------|------|
| `**/App.tsx` | React 主应用壳 |
| `**/entry-server.tsx` | SSR 服务端入口 |
| `**/entry.tsx` | CSR 入口（TSX） |
| `**/entry.ts` | CSR 入口（TS） |
| `**/entry.js` | CSR/legacy JS 入口 |

若有 `store/` 等且无 `App.tsx`，仍在矩阵中占位一行（入口类型填「—」或备注 legacy）。

### Step A2 — **功能点 + 横切层** 扫描（非 page 专属）

在 **不替代 A1** 的前提下补充：

1. **`src/common/**` 顶层**：在 **`PROJECT_FEATURE_DETAILS.md` → 「功能维度」** 中建 **`feat-common`**（或等价）说明 D2；在 **`PROJECT_OVERVIEW.md`** 仅 **一行表**/`src/` 列表级提及即可。
2. **功能矩阵**（D3）：放在 **`## 功能维度`** 开头的 **索引表**，并保证每个能力有独立 **`feat-*`** 小节（可粗可细）；列内写 **Related `detail-NN`** 与关键路径。
3. 若用户点名 **某一功能**（例如「目录树懒加载」）：先在矩阵中定位行，再同时打开 **所有相关 page 小节** 与 **`src/common` 实现**，避免只盯一个 `App.tsx`。

### Step B1 — 写 / 更新 **`docs/PROJECT_OVERVIEW.md`（总览定额）**

1. **项目一句话**、技术栈、`pnpm`/`npm` 等 **如何运行**（只写 `package.json` 里有的脚本，端口不明则写「以环境为准」）。
2. **仓库地图**：`src/pages`、`src/common`、生成 API 目录等 **表格化**。
3. **一页架构**：**一张** `flowchart`（覆盖 A1 全部 page + Common + API），**仅放在本文件——详情内禁止重复整图**。
4. **详情导航**：**两类** Markdown 表格链到详情：`Page → #detail-NN`，`Feature → #feat-*`；并链接 **`#page-dimension-marker`** / **`#feature-dimension-marker`** 任选。
5. **不要**在此处再贴 D1/D3 大块矩阵（已与详情重复易造成三源）；**不要**在此处写状态机 / 长篇 `ts` 契约。
6. **推荐阅读路径** + `Unknowns`。

### Step B2 — 写 / 更新 **`docs/PROJECT_FEATURE_DETAILS.md`（详情 = Page + Feature）**

1. **`## Page 维度`**：`### Page 入口矩阵（D1）`；每个 `src/pages` 子目录：**`<a id="detail-NN"></a>`**，再接 **三级标题**（Markdown 中用反引号包目录名：`### coohom-help-center`）；配额：协作 + 数据流 + **≥1 stateDiagram** + **≥1 ts 契约**（已深描包）；仅索引用短段 +（可选）小图。
2. **`## 功能维度`**：**`<a id="feature-dimension-marker"></a>`** + **索引表**（D3）；每个能力 **`<a id="feat-*"></a>`** + 小节：**端到端**图 / 状态 / 契约，并 **hyperlink 回相关 `detail-NN`**，而不是再写一页「总览简介」。
3. **严禁**在本文件顶层写「请先读总览」类教程腔；**严禁**嵌入与 `PROJECT_OVERVIEW.md` **同义的整仓鸟瞰 Mermaid**（否则双源）。
4. 若读者可能单开详情：**首段用 3–4 行说明二维结构即可**，不出现项目 slogan 级总览正文。

### Step C — 全局架构图

**仅存在于 `PROJECT_OVERVIEW.md`**。

### Step D — 深描范围与 **已深描** 定额（仅作用于 `PROJECT_FEATURE_DETAILS.md`）

#### `已深描`（单行从「仅索引」升级时必须凑齐）

每个 **已深描** 小节 **至少包含**：

1. **图说明**（短文：谁调用谁、数据从哪到哪）
2. **组件协作** `flowchart`
3. **数据流** `flowchart`（或与 SSR 注水等价的一条链路图）
4. **状态机**：**至少 1 个** `stateDiagram-v2`，覆盖该页或该小节主题的 **主要生命周期或异步阶段**（例：SSE  Idle→Streaming→Done/Aborted；SSR Redirect→Fetch→Render；目录树 NavPath→Ready→Expanding；搜索 Submit→Loading→Result）。无复杂状态时用简图，但不可用纯文字代替。
5. **类型契约**：**至少 1 个** ` ```ts ` 代码块，描述与本页/本能力最相关的 **抽象类型**（store 形态、关键 props、SSE chunk/done、页面模型字段subset）。首行注释写 `// 简化自 <path>，非逐字拷贝`。
6. 若含 **多角色 IO**（用户、浏览器、服务端）：**建议** 再配 `sequenceDiagram`。

#### `仅索引`

- **小图**（如单条 `flowchart LR`）+ 职责 + 路径；**不强制**状态机与契约；若仍有异步（如重定向），**建议**保留迷你 `stateDiagram-v2`。

#### 默认优先级

- 先保证 **A1 表 + A2 功能点表 + 全局图**；再按用户 P0 或既有「已深描」行补全 **状态机 + 契约**。
- 用户指定页面 / P0 列表：对该子集升级为 `已深描` 并满足上表定额。

### 禁止

- **不要创建** `docs/PROJECT_FEATURES.md`（或其它第三份主线分解文档）。
- 不要把 **多节 stateDiagram / 大块契约** 写进 **`PROJECT_OVERVIEW.md`**。
- **不要把「整仓一页架构」或项目总览正文**写进 **`PROJECT_FEATURE_DETAILS.md`**。
- 不要用「要不要补第 N 页」阻塞 **A1 + B1（总览骨架）** 与 **Page/功能两块详情骨架**。
- 默认 **不** 新建 `docs/diagrams/*.mmd`。

## Operating rules

- Prefer **reading a few high-signal files** over scanning everything.
- Use tools in this order: **Glob → Grep → Read → SemanticSearch** (only widen scope when stuck).
- Avoid “guessing”; when uncertain, mark items as **Unknown** and add a **Follow-ups** list.
- Keep artifacts concise and navigable; link to real file paths.
- If generating a `.canvas.tsx` file: **first read** the built-in Canvas skill at `~/.cursor/skills-cursor/canvas/SKILL.md` and follow it.

## What to output (default deliverables)

### A) `docs/PROJECT_OVERVIEW.md`

Create/update this file in the repo with the template below.

```markdown
## Project overview
- **One-liner**: <what this product is>
- **Tech stack**: <frameworks/libs/build tooling>
- **How to run**: <commands, env vars, ports>

## High-level structure
- **Entry points**: <main entry files / bootstrap>
- **Pages / routes**: <route table or list>
- **Feature / capability map** (cross-page): <e.g. AI, search, auth — which packages & `common/` paths>
- **Core modules**: <top-level domains/features>
- **Shared layers**: <components, utils, services, store>

## Key data flows (end-to-end)
### Flow 1: <user action → UI → state → API → UI>
- **Trigger**: <event>
- **Modules**: <files/functions>
- **State**: <store slices/contexts>
- **Network**: <API endpoints / SSE / WS>
- **Rendering**: <components>

### Flow 2: ...

## Architecture diagrams
- **High-level only in this overview file** (`PROJECT_OVERVIEW.md`).  
- **All per-feature diagrams** live in `PROJECT_FEATURE_DETAILS.md`. Standalone `.mmd` only if the user explicitly asks.

## Unknowns / assumptions
- <bullet list>

## Recommended reading path (30–60 minutes)
1. <file>
2. <file>
...
```

### B) Mermaid diagrams embedded in markdown docs (default)

Do **not** create separate `.mmd` files by default.

Embed Mermaid blocks directly in:
- `docs/PROJECT_OVERVIEW.md` — **only** **one** system-wide `flowchart` (required) and optionally **one** short whole-product `sequenceDiagram`. No state machines here.
- `docs/PROJECT_FEATURE_DETAILS.md` — **Page 小节**：协作 + 数据流 + **`stateDiagram-v2`** + 可选 sequence；**功能 小节（`feat-*`）**：端到端抽象图 + **`stateDiagram-v2`** + 契约。**禁止**在此处再放与总览重复的整仓架构 `flowchart`。

Use:
- `flowchart LR` / `TB` for architecture/dataflow / collaboration
- `sequenceDiagram` for user ↔ client ↔ network journeys
- **`stateDiagram-v2`** for discrete phases (SSR, SSE, tree expand, form submit, redirects)
- **` ```ts `** contract blocks next to the diagrams they document (store shape, API payloads, SSE events)

### D) Feature-first deep dives (when requested)

When the user asks about a **specific feature** (e.g. “目录树加载过程”, “侧边栏知识问答”):

1. Resolve the feature in **`PROJECT_FEATURE_DETAILS.md` → 「功能维度」索引表**：列出涉及的 **`detail-*`** 页与 `common`/`api`；在对应 **`feat-*`** 小节补图与契约。**`PROJECT_OVERVIEW.md`** 只同步 **feat 锚点索引行**。
2. Produce **per feature**:
   - **Component collaboration** `flowchart`
   - **Dataflow** `flowchart`
   - **`stateDiagram-v2`** for the feature’s main state machine
   - **` ```ts `** contract block (shared types / API)
3. If async IO is central, add **`sequenceDiagram`**.
4. Optionally add a **cross-page mini map** (one `flowchart`) when the feature spans multiple `src/pages/*` packages.

### C) Optional Canvas: `docs/project-map.canvas.tsx`

If the user asked for a “直观/可交互项目地图”, create a Canvas that:
- Lists major modules (clickable file links if supported)
- Shows 1–3 key flows with edges
- Includes a “reading path” panel

If the project is small, skip Canvas and just do Mermaid + markdown.

## Discovery workflow (how to decompose any project)

### Step 0: Establish scope quickly
- Identify: frontend/backend/monorepo? primary language? build tool?
- Find: `package.json`, `tsconfig.json`, `vite.config.*`/`webpack.*`, `next.config.*`, `pnpm-workspace.yaml`, `turbo.json`, `apps/`, `packages/`, `src/`.

### Step 1: Map entry points & bootstrapping
- Find app entry: `src/main.*`, `src/index.*`, `app/*`, framework-specific entry.
- Read 1–3 files to locate: router, providers, store setup, API client setup.

### Step 2: Map pages/routes
- Detect routing style:
  - React Router: `Routes`, `createBrowserRouter`, route config files.
  - Next.js: `pages/` / `app/` directory.
  - Other: grep for `route`, `router`, `path`, `navigate`.
- Output a compact “Routes → Page component → data dependencies” list.
- Also output a **`feature / capability`** list (cross-page behaviors): derive from folders like `modules/`, `features/`, `src/common`, and repeated domain words in stores.

### Step 2b: Map shared layers & APIs (parallel to Step 2)
- Glob **`src/common/**`** (or `packages/*/src`, `libs/`) and note **consumers**: grep imports from `@/common` / package name per page entry.
- Note **generated or centralized API** dirs (e.g. `.cloudapi`, `openapi`, `graphql`).

### Step 3: Map state & data sources
- Identify state approach: Redux/Zustand/MobX/Context/useReducer/custom store.
- Identify IO: `fetch` wrappers, `axios`, GraphQL, SSE/WebSocket, localStorage.
- Extract “state graph”: slices/contexts → consumers → producers.

### Step 4: Choose 1–3 critical flows and trace end-to-end
Pick flows that touch most layers:
- “Open page” / “Search” / “Create” / “Send message” / “Save”
For each flow:
- Start from UI event handler
- Follow into store/action/service
- Follow into API/SSE callbacks
- Follow response into state update and render

### Step 4b (feature-first): Deep dive a page/feature

If the user wants “以功能、页面为切入入口”:
- **HelpCenter**：**B1** 放 **唯一**全景图（**不得在详情重复**）；**B2** 先 **Page 骨架**（D1+各包）再 **功能骨架**（D3索引+各 `feat-*`）；升级版 `仅索引` → `已深描` 时：**Page** 小节与 **对应功能** `feat-*` 可同时加厚，但总览仍 **只保留链接表**。
- **By page**：Pick `App.tsx` / `entry-server` / `entry.*` → `modules/*`, store, APIs.
- **By feature**：Start from **D3 row** → all related pages + **`src/common`** implementations.
- Trace **load path** and **interaction path**; then freeze them into **state machine** nodes (aligned with reducer phases, `useEffect` stages, or SSR branches).

### Step 5: Generate artifacts + validate
- Ensure diagrams reflect real file paths.
- Cross-check with Grep on key symbols to avoid missing alternative paths.
- Add Unknowns + Follow-ups.

## Quality bar checklist

- [ ] **`docs/PROJECT_OVERVIEW.md`**：**唯一整仓架构图**；**无**深描/state/长契约；含 **两段**跳转表：**Page `#detail-*`** + **`Feature #feat-*`**
- [ ] **`docs/PROJECT_FEATURE_DETAILS.md`**：**`## Page 维度`** + **`## 功能维度`** 齐全；不含总览复述、**不含**第二张整仓鸟瞰图；D1 与 `src/pages` 对齐；每个 **已深描** Page **或** Feature 块含协作 + 数据流（或端到端等价图）+ **≥1 stateDiagram-v2** + **≥1 TS 契约**
- [ ] **不存在** `docs/PROJECT_FEATURES.md`（或已删除旧文件）；仅 **两主线**：`PROJECT_OVERVIEW` / `PROJECT_FEATURE_DETAILS`
- [ ] At least 1 system **architecture** `flowchart` in the overview (all repos)
- [ ] At least 1 **dataflow** `flowchart` per deep-dive section in details (or per feature bucket)
- [ ] At least 1 **`stateDiagram-v2`** for the repo’s nastiest lifecycle (SSE, SSR, wizard, multi-step forms)
- [ ] At least 1 sequence diagram for the most important network-heavy flow (when applicable)
- [ ] If feature-first was requested: diagrams + **state machine + TS contract** per feature slice
- [ ] “Reading path” points to the most important files
- [ ] Unknowns are explicit (no silent guessing)

