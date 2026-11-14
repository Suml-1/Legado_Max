# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

阅读Max (legado_Plus) — an Android e-book reader app forked from Legado. Supports custom book sources with user-defined rules (Jsoup selectors + Rhino JS), RSS subscriptions, local TXT/EPUB reading, and an embedded HTTP/WebSocket server for remote control.

## 本文件定位

CLAUDE.md 是项目级稳定契约，不是状态记录。只收录：固定规则、项目定位与架构、代码约定、构建/发布/验收规则。禁止写入：当前进度、临时风险、逐次日志、待办清单、个人备注——这些状态分别归 issue 追踪、CHANGELOG 与 git 历史；需要过程性说明时写到 `docs/` 对应目录，不要追加到这里。

## Build Commands

Gradle wrapper（Windows 下 `gradlew.bat`），JDK 17。常用命令（完整矩阵与解释见 [docs/project-rules/build-commands.md](docs/project-rules/build-commands.md)）：

```bash
./gradlew assembleDebug            # Debug 构建（默认 flavor：appMax）
./gradlew installAppMaxDebug       # 安装到设备
./gradlew test                     # 单元测试
./gradlew lint                     # Android Lint，本地最后门禁（验证顺序：单测 → lint）
./gradlew app:downloadCronet       # 首次构建前必须跑，下载 Cronet 原生库
./gradlew assembleDebug --warning-mode all   # 查看 DSL 语法警告
```

Web 前端（`modules/web/`，嵌入 HTTP 服务的前端，Vue 3 + Vite）构建命令见同一文档的 Web 前端小节，需 Node >= 20、pnpm >= 9。

## Architecture

MVVM pattern with AndroidViewModel + ViewBinding + Coroutines.

### Base Classes (`io.legado.app.base`)

- `BaseActivity<VB>` — all Activities extend this. Manages theming, system bars, view binding. Override `observeLiveBus()` for event subscriptions (auto-cleaned on destroy).
- `VMBaseActivity<VB, VM>` — adds abstract `viewModel` property.
- `BaseViewModel` — extends `AndroidViewModel`. Key method: `execute { }` returns a `Coroutine<T>` with chainable `.onSuccess`, `.onError`, `.onFinally`. Default context is `Dispatchers.IO`, callbacks on `Dispatchers.Main`.

### Key Patterns

- **Coroutine helper**: `BaseViewModel.execute()` wraps `Coroutine.async()`. Use this instead of raw `viewModelScope.launch`.
- **Event bus**: `LiveEventBus` for cross-component events. Subscribe via `observeEvent<T>(key) { ... }` in `observeLiveBus()`.
- **Database**: Room (`AppDatabase` v101), singleton at `appDb`. DAOs in `data/`, entities in `data/entities/`. Uses KSP (not kapt).
- **Book source rules**: HtmlUnit core-js (Rhino fork, `:modules:rhino` module) evaluates user-defined rules. Declarative rules are parsed by `model/analyzeRule/`; 单文件 JS 书源（`book_sources.mainJs`）run through `model/jsSource/`.
- **Singletons in model/**: `ReadBook`, `CacheBook`, `AudioPlay` manage global reading state.
- **Config packages**: `TopBarConfig` and `BubblePackageManager` store configs as file system directories (JSON + assets like wallpapers/icons), not SharedPreferences. `NavigationBarConfig` uses SharedPreferences. `ApplicationThemeManager` combines all sub-configs into exportable/importable theme packages (zip).

### Modules

The project has three library modules in `modules/`:

- `modules/book` — fork of epublib (EPUB parsing), package `me.ag2s.epublib`
- `modules/rhino` — wraps HtmlUnit core-js (the HtmlUnit fork of Rhino; engine classes live in `org.htmlunit.corejs.javascript`), module package `com.script`. Evaluates user-defined book source rules at runtime. Release builds must keep `org.htmlunit.corejs.javascript.**` (see `modules/rhino/consumer-rules.pro`); update that rule whenever the engine artifact/package changes, otherwise R8 minification breaks every script at runtime while debug builds keep working.
- `modules/web` — Vue 3 frontend for the embedded HTTP/WebSocket server (see above)

### Source Layout

`app/src/main/java/io/legado/app/`:

- `ui/` — Activities/Fragments grouped by feature (book/, rss/, source/, config/, debuglog/, image/)
- `model/` — domain logic (WebBook for HTTP fetching, analyzeRule for rule engine, ParagraphBubbleRenderer, BookCover)
- `data/` — Room DB, DAOs, repositories
- `help/` — helpers (config managers for theme/navbar/topbar/bubble, http client, coroutine utilities, source management)
- `lib/theme/` — theme utilities (accent colors, typography, corners, page colors, TitleBar config extensions)
- `utils/` — Kotlin extensions (~90 files)
- `web/` — embedded NanoHTTPD server + WebSocket endpoints

### Compose Usage

Jetpack Compose (Material3) is used for a growing share of UI: standalone pages (Activities extending `BaseComposeActivity`, e.g. theme manage, storage manage, debug tools) and ComposeView surfaces embedded in View-based screens (home page, search, book info). The traditional View system (ViewBinding + XML layouts) remains the base of most existing screens. Both coexist long-term — ComposeViews can be overlaid on View-based Activities.

Compose 规范拆分为 8 个文件，位于 `docs/project-rules/compose/`（目录结构/状态事件/主题样式/性能/导航/无障碍/测试/迁移审查，索引见 [README.md](docs/project-rules/README.md)）。**写 Compose 前先按主题读对应文件**；迁移老代码时重点对照 [`compose/migration-review.md`](docs/project-rules/compose/migration-review.md)。

## 项目级规范（必读）

项目级强制规范库位于 `docs/project-rules/`，索引与领域覆盖矩阵见 [`docs/project-rules/README.md`](docs/project-rules/README.md)。写代码前先按"什么时候必须读"对照索引；改动代码后主动回看相关规范是否需要同步更新（规范跟着代码走，pre-commit 的 help-doc-sync 钩子会拦截"改了受管代码却没改对应文档"的提交）。

- **协程**：本项目使用自研链式协程包装（`BaseViewModel.execute` → `help/coroutine/Coroutine`）。使用协程前必读 [`docs/project-rules/coroutine-rules.md`](docs/project-rules/coroutine-rules.md)，其中包含 `execute` 链的时序坑、Scope 规则、Flow 位置与反面示例。
- **数据层（Repository）**：[`docs/project-rules/repository-rules.md`](docs/project-rules/repository-rules.md)，新增数据访问逻辑必须遵循。
- **API 兼容**：[`docs/project-rules/api-compat-rules.md`](docs/project-rules/api-compat-rules.md)。调用高于 minSdk 23 的 API、引入新依赖、发版前必读（SDK 分支写法、desugaring 边界、16KB 对齐等 targetSdk 37 红线）。
- **事件总线**：[`docs/project-rules/live-event-bus-rules.md`](docs/project-rules/live-event-bus-rules.md)。新增跨组件事件、在 LiveEventBus 与 Compose `Channel<Event>` 之间选型时必读。
- **更新日志规范**：[`docs/project-rules/update-log-rules.md`](docs/project-rules/update-log-rules.md)。提交 app 用户可见改动（bug/界面/功能）后，按其中时机与收录范围维护 `app/src/main/assets/web/help/md/updateLog.md`；发版前必须更新到位。
- **架构与设计说明**：[`docs/architecture/`](docs/architecture/) 存放长期有效的模块架构、设计方案、技术笔记与测试/验证方法论，**全部文档的分组索引见 [`docs/architecture/README.md`](docs/architecture/README.md)**（新增文档必须登记，别让它躺在目录里没人知道——与 `docs/project-rules/README.md` 同理）。想了解某个模块"现在是怎么设计的"先查该索引；一次性改造方案在 `docs/archive/`，两者不要混。

### 计划/方案文档的收尾

`docs/` 根目录不放散文档，只保留各规范子目录。任务收尾（实现 + 验证 + code-review + 提交）时，同一批处理本次产生的方案文档，不留悬空计划：

- **一次性方案 / 计划 / 根因分析**（文件名含「方案」「计划」「分析」「评估」）：实现落地后 `git mv` 到 `docs/archive/`，沿用原文件名，不加日期前缀。
- **结论已沉淀进代码和规范**的：直接删除，避免同一事实两处维护、日后文档与代码不同步。
- **长期有效的约定 / checklist**：下沉到 `docs/project-rules/`，并在 `docs/project-rules/README.md` 索引登记。
- **长期有效的架构说明**（模块架构、设计方案、技术笔记、功能说明）：放入 `docs/architecture/`，并在文件开头写明适用范围与是否仍然有效。

判断依据：下次有人问"这块怎么做的"时还会不会来读这份文档——会，就进 `docs/architecture/` 或下沉成规范；不会，就归档或删除。

> **代码永远比文档准确**：文档只记录某一个时刻的状态，会随迭代腐化。任何时候发现文档与代码不符，一律**以代码为准**，并顺手修正文档（或标注"已过时，见 xxx"）。文档是导航不是契约——用它找方向，别用它下结论。

## 核心规则

1. **设计先于编码** — 收到功能需求时，先检查可用的 Skill 并加载匹配项做需求分析（详见「Skill 的使用」）
2. **测试先于实现** — 写代码前先写测试（TDD）；按开发环境灵活执行，环境不允许就地跑测试时不强求（见 Testing Strategy）
3. **验证先于完成** — 声称完成前必须运行验证命令
4. **发现无关 bug/优化 → follow-up 报告**：任务过程中发现的 bug 或优化点，如果与当前 change 无关，不在本次修，而是作为 follow-up 报告单独提出。
5. **任务有歧义时选最直接的理解**：不要把其他可能的理解也一起做了，只实现最直接的那个理解。
6. **测试文件只在需要时提交**：任务没有明确要求、仓库惯例也不需要时，不提交测试文件。需要提交时，规模参照旁边已有的测试文件。
7. **沟通节奏**：开始执行任务之前，用一句话说明即将要做什么；工作过程中给出简短的进度更新；结尾写一段可以独立看懂的简短总结——发现了什么、做了什么、下一步是什么——让只看到最后一条消息的读者也能了解全貌。
8. **改完代码后走 code-review**：任务完成、代码写完后，自动调用 code-review skill，按 Standards（是否符合项目编码规范）和 Spec（是否符合需求 spec）两个维度审查 diff，两份报告独立输出、不合并不排序。
9. **复杂 bug/调试直接走诊断技能** — 遇到无法一眼定位的复杂 bug、性能回归或难排查的调试问题时，先加载 `diagnosing-bugs` 技能按流程收窄根因（先写到最小复现、建立「复现 — 修复 — 回归验证」反馈闭环），不要凭感觉直接改代码猜。
10. **架构改进走架构诊断技能** — 用户想改进架构或要求产出架构诊断报告时，加载 `improve-codebase-architecture` 扫描出深化改进点、产出报告后再动代码。
11. **学习项目知识走 teach 技能** — 用户明确表示想学习某个概念、模块或技能时，加载 `teach` 组织讲解，不要泛泛而谈或只甩源码。
12. **术语歧义走 domain-modeling** — 讨论中出现术语含义不一致、概念边界模糊或需要记录架构决策时，加载 `domain-modeling` 统一术语并把结论写进 `docs/adr/`（决策）与根目录 `CONTEXT.md`（术语表 / ubiquitous language，首次用到该 skill 时创建）。
13. **不确定的技术事实走 research** — 涉及外部 API、规范、库行为、版本差异等凭记忆说不准的事实时，先走 `research` 用高置信一手资料查证并落成文档结论，不凭记忆作答。
14. **合并/重摊冲突走 resolving-merge-conflicts** — 遇到 git merge 或 rebase 冲突时，加载 `resolving-merge-conflicts` 按意图逐个 resolve（**绝不 `--abort`、不随手挑一行**），全部解决后再完成操作。
15. **写智能体文档走 writing-for-agents** — 需要新建或修订 AGENTS.md / CLAUDE.md、编写与重构 skill、按层级深化一套复杂智能体文档体系（context pointer、分支逻辑、information hierarchy），或排查 Agent 执行过程不稳定（variance）时，加载 `writing-for-agents`，按其指针 sharpening、progressive disclosure、leading word、pruning 原则落笔。
16. **要看图理解走 show-me** — 用户想直观理解或对比某个逻辑/算法、控制流、UI 结构、数据流或大范围文件布局时，加载 `show-me`，用伪代码/调用树/组件树/Mermaid/diff/HTML 等最小可视图配简短文字讲清。
17. **消息没看懂走 wait-what** — 用户表示上一条 AI 消息没看懂、要求说人话/用通俗的话重新解释时，加载 `wait-what`，抛开术语和上下文用日常语言把同一件事再讲一遍，而不是复读原文。

## Coding Conventions

- Kotlin 代码风格遵循 Google Android Style Guide
- 命名规则：Activity `XxxActivity`、ViewModel `XxxViewModel`、Fragment `XxxFragment`
- 日志 tag 统一 `AppTag.<类名>` 格式（如 `private const val TAG = "AppTag.BookInfo"`），**新代码强制**；此规则为**前瞻目标态**（全库尚无实例），存量已有的 TAG 常量不要求回改。

## Comments

> 注释优先表达**为什么这么做、特殊约束、业务背景**，代码本身负责表达”是什么、怎么做”

- **类注释**：核心/复杂类（单例、引擎、解析器、管理器）必须完整 KDoc；普通 `Activity` / `Adapter` / `ViewModel` 简短说明核心用途即可。
- **函数注释**：对外 API、复杂业务逻辑、有特殊入参/返回值约束的函数必须 KDoc；简单 getter/setter、私有简单函数不写。
- **变量注释**：优先用命名表达语义；仅业务含义隐晦、有特殊边界约定时补一行。

## Dependency Management & Version Catalog

- 所有依赖版本通过 `gradle/libs.versions.toml` 统一管理，在 `build.gradle` 中按 `libs.xxx` 引用（本项目构建脚本为 Groovy，没有 .gradle.kts）；禁止硬编码版本号；新增依赖需同步更新版本目录文档。
- 主版本与 SDK 信息（Kotlin / Hilt / OkHttp / Room / Compose BOM 等）见 [docs/project-rules/build-commands.md](docs/project-rules/build-commands.md)。

## Testing Strategy

单元/集成测试位置、覆盖率约定、Mockk / kotlinx-coroutines-test / LeakCanary 说明见 [docs/project-rules/testing.md](docs/project-rules/testing.md)。

真机 / 端到端验证方法论（按需选用，完整分组见 [`docs/architecture/README.md`](docs/architecture/README.md) 第二节）：

- **Web 服务三层验证**：强制规范 [docs/project-rules/e2e-testing-rules.md](docs/project-rules/e2e-testing-rules.md) + 操作手册 [`docs/architecture/Web服务端到端测试方法.md`](docs/architecture/Web服务端到端测试方法.md)。
- **数据驱动功能免点击 adb 验证**（非 Web：书源 / JS 调试页等，靠 logcat 锚点 + run-as 注数据 + am start 直启调试页）：[`docs/architecture/adb免点击验证数据驱动功能.md`](docs/architecture/adb免点击验证数据驱动功能.md)。
- **E2E 概念与自动化框架选型**：[`docs/architecture/E2E测试概念与自动化框架选型.md`](docs/architecture/E2E测试概念与自动化框架选型.md)。
- **本地 JVM 探针“量尺”测试**（不装 APK，跑两段候选代码快速量化差距并断言等价）：[`docs/architecture/JVM探针测试方法.md`](docs/architecture/JVM探针测试方法.md)。

## Build Variants

三个 product flavors（维度 "app"）：`appLegacy`（`io.legado.app`，与原版同包名）、`appMax`（`io.legado.app.yuedu`，主开发目标）、`appS`（`io.legado.app.yuedu.a`）；debug/release 均带 `applicationIdSuffix`。SDK 版本（minSdk 23）、desugaring、release 混淆等完整配置见 [docs/project-rules/build-commands.md](docs/project-rules/build-commands.md)；API 兼容红线见 [api-compat-rules.md](docs/project-rules/api-compat-rules.md)。

## CI/CD

GitHub Actions 全部位于 `.github/workflows/`，各 workflow 的职责与触发条件见 [docs/project-rules/ci-cd.md](docs/project-rules/ci-cd.md)（test.yml 构建并发布、web.yml 构建 Vue 前端、cronet.yml 更新 Cronet、lint.yaml 跑 lint 等）。CI 中 lint 通过视为"完成"的一部分。

## Conventions

- `NonTransitiveRClass` is enabled — reference only directly used resources.
- Room schema exports to `$projectDir/schemas` for migration verification.
- Default-disabled build features (via `gradle.properties`): resvalues, shaders. buildConfig is explicitly enabled (Cronet version fields); do not assume BuildConfig is absent.
- 架构文档有两个家，职责不同、不要混：**`Structure/`（中文）** 是项目**整体架构总览与学习笔记**——启动流程、数据库架构、阅读核心流程、事件总线、模块依赖关系等“全应用层面”的叙述；**`docs/architecture/`** 是**逐模块/专题的长期设计说明**（Cookie 管理、高亮规则、Web 服务架构等）。新增“某一个模块长期怎么做”的设计文档进 `docs/architecture/`（见上文「计划/方案文档的收尾」）；“整个项目怎么搭起来”的总览仍留在 `Structure/`。两者都遵循“文档是导航不是契约、以代码为准”。

## Git Commit 规范

Conventional Commits 中文适配，husky + commitlint 自动校验不合规提交。

- 格式：`<type>(<scope>): <subject>`，type 用英文（feat/fix/docs/refactor/perf/test/chore/ci/revert/style），scope 和 subject 用中文，subject 为动宾短语且 ≤ 100 字符。
- 交互式提交：`npm run commit`；**新人首次使用需在项目根目录运行 `npm install`**，否则 git hook 不会生效。
- **帮助文档同步门禁（pre-commit）**：改了受管代码区域（映射表 `docs/help-doc-sync/map.json`，脚本 `scripts/help-doc-sync.mjs`）但没同步改对应 md，commit 会被拦下并给提示；确认本次改动不涉及文档内容时可用 `SKIP_DOC_SYNC=1 git commit ...` 逃生；扩展映射只需编辑 map.json，无需改脚本。
- 详细规范、反例与常见问题见 `docs/git-hook/`。

### AI 与危险 git 命令（强制）

以下命令属于**不可逆或影响共享历史**的操作，AI **一律不得自动执行**；确需执行时必须先说明原因、取得用户明确授权后才可继续：

- **push 相关**：`git push`、`git push --force` / `-f` / `--force-with-lease`；禁止直接推送 `main` / `master`。
- **历史改写**：`git reset --hard`、`git rebase`、`git commit --amend`。
- **工作区破坏**：`git clean -fd`、`git checkout -- <file>`、`git restore <file>` 等会覆盖未提交改动的操作。

硬拦截由 `.husky/pre-push` 兜底（拦 `main` / `master` 与 non-fast-forward）。`reset --hard` / `clean` / `checkout` / `restore` **没有任何 git hook 可以拦截**，只能依赖本约定。

## Skill 的使用

当任务匹配某个 skill 时，使用 `Skill` 工具加载对应 skill 并严格遵循其流程。绝不要用 Read 工具读取 SKILL.md 文件。

当任务明确匹配某个 skill 的应用场景时，应调用该 skill 检查。

仓库内置技能镜像位于 `.claude/skills/`（含 `legado-*` 项目专属技能，清单与更新策略见该目录 README）；运行时以当前环境可加载的技能注册表为准。

## 代码搜索（优先用 rg）

搜索代码优先用 `rg`（ripgrep）：

- 默认尊重 `.gitignore`，不会把 `build/` / `.gradle/` / `node_modules/` 的生成物卷进结果，速度也快得多
- 环境里没有 rg 时退回 `grep -rn` / `find ... | grep`，不必强装；Windows 上可顺手装：`winget install BurntSushi.ripgrep` 或 `scoop install ripgrep`
