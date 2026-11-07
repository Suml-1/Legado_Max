# project-rules 索引

> 本目录是 Legado_Max 的**项目级强制规范库**，不是教程，是红线。
> 新代码必须对照这里写，Code Review 以此目录为标尺；标 `[强制]` 的条目由 spotless(ktlint)/Android lint/CI 或人工 Review 兜底。
>
> **阅读顺序建议**：`coroutine-rules.md`（所有异步代码都绕不开）→ `repository-rules.md`（碰数据层）→ 写 Compose UI 时再进 `compose/`。

## 一、当前规范文件

### 全局（不分 UI 技术栈）

| 文件                                                 | 管什么                                                                                                                                                                                          | 什么时候必须读                                                                                                         |
| ---------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| [coroutine-rules.md](./coroutine-rules.md)           | 自研链式协程包装 `Coroutine<T>` / `execute` 的用法与红线、Flow 使用位置、Compose 场景红线、反面示例、测试                                                                                       | **写任何异步代码前**。核心内容：`execute` 回调时序坑、禁止 GlobalScope/runBlocking、View 系 vs Compose 系的 scope 边界 |
| [repository-rules.md](./repository-rules.md)         | Room 数据层三层架构（Entity → DAO → Repository）、Entity 禁止 Active Record、新代码标准写法、迁移策略                                                                                           | 碰 DB / 新建 Repository 前                                                                                             |
| [api-compat-rules.md](./api-compat-rules.md)         | minSdk 23 / targetSdk 37 红线、`Build.VERSION.SDK_INT` 分支写法、Desugaring 覆盖边界、依赖 minSdk 红线（禁 overrideLibrary）、16KB 对齐 / Edge-to-Edge / FGS type 等 targetSdk 37 行为收紧      | 调用高版本 API、引入新依赖、发版前必查                                                                                 |
| [live-event-bus-rules.md](./live-event-bus-rules.md) | LiveEventBus 全局配置语义（`autoClear(false)` 粘性默认开）、tag 必须走 `EventBus` 常量 + reified 封装、高频事件节流红线、与 Compose `Channel<Event>` 的选型边界                                 | 跨组件通信 / 新增事件 / View↔Compose 事件选型时                                                                        |
| [e2e-testing-rules.md](./e2e-testing-rules.md)       | 内置 Web 服务改动的强制 E2E 三层验证：触发时机、接口/网页/App 端验收标准、失败路径要求、环境降级策略；操作命令在 architecture 手册                                                              | 改动 `web/`、`api/` 或网页前端（assets / modules/web）的行为后、声称完成前必读                                         |
| [intent-payload-rules.md](./intent-payload-rules.md) | Intent/Bundle 大载荷 Binder 事务超限红线：超约 3.2 万字符禁止直传、`TransactionTooLargeException` 会 SIGKILL 前台进程（易被误当内存问题）、大文本走 `CodeEditLauncher` 内存中转、去回程都要收口 | 往 `putExtra` 放可能很大的文本/对象（编辑器、整源 JSON、调试响应、SVG/HTML）、排查"打开大内容就黑屏重启"时             |

### 工程配置参考（手册，非红线）

> 以下文件是**工程配置参考 / 手册**，不是强制规范：内容随工程调整，不进入 Review 标尺。

| 文件                                         | 内容                                                                                                 | 什么时候读                                                                             |
| -------------------------------------------- | ---------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------- |
| [build-commands.md](./build-commands.md)     | 构建命令全表、Web 前端命令、3 个 flavor、SDK/JDK/版本目录                                            | 构建报错、首次构建、不认识 flavor / 命令、动 `libs.versions.toml` 或 `modules/web/` 时 |
| [ci-cd.md](./ci-cd.md)                       | `.github/workflows/` 各 workflow 职责与触发条件、发布正文的组成与贡献者章节生成规则                  | CI 报错、调整 workflow、确认发版 / 构建流程、改发布说明内容时                          |
| [android-lint.md](./android-lint.md)         | Android Lint 工具说明：与普通 lint 的区别、查什么、基线机制、本地看报告                              | 看不懂 lint 报错、想用 lint 检查新代码时                                               |
| [testing.md](./testing.md)                   | 单元/集成测试位置与命令、覆盖率口径、Mockk / coroutines-test / LeakCanary、测试文件提交约定          | 写测试前、跑测试、决定提交测试文件时                                                   |
| [update-log-rules.md](./update-log-rules.md) | 对外更新日志 `app/src/main/assets/web/help/md/updateLog.md` 的更新时机、收录范围、格式与用户措辞约定 | 提交 app 用户可见改动后、发版前维护 `updateLog.md`，或 Review 日志条目时               |

### Compose UI 层（`compose/` 子目录）

| 文件                                                             | 管什么                                                                                       | 核心红线                                                     |
| ---------------------------------------------------------------- | -------------------------------------------------------------------------------------------- | ------------------------------------------------------------ |
| [compose/README.md](./compose/README.md)                         | 总纲：生效范围、机器/人工执行方式、老代码策略、8 文件索引                                    | 机器强制机制（tools/lint-rules）尚未建立，现阶段人工对照     |
| [compose/structure.md](./compose/structure.md)                   | Compose 目录结构、命名、API 契约、组件拆分标准                                               | Screen / StateHolder / 子组件职责边界                        |
| [compose/state-events.md](./compose/state-events.md)             | `StateFlow<UiState>`、`Channel<Event>` 缓冲区语义、Dialog 条件渲染、`repeatOnLifecycle` 绑定 | 一次性事件缓冲区必须显式指定；ViewModel 禁止直接操作平台 API |
| [compose/theme-styles.md](./compose/theme-styles.md)             | 颜色、dimens、图片加载（Glide 链路）、字体、字符串、动画三档时长、主题切换与页面重建         | 禁止魔法数字；主题切换禁止裸 `recreate()` 原地重建           |
| [compose/performance.md](./compose/performance.md)               | Recomposition 防范、`derivedStateOf`、LazyColumn key + stable 参数                           | 不稳定参数导致多余重组；禁止 Composable 内直接读 DB          |
| [compose/navigation-preview.md](./compose/navigation-preview.md) | 路由集中定义、Preview 规范                                                                   | 路由不散落                                                   |
| [compose/accessibility.md](./compose/accessibility.md)           | `contentDescription`、`semantics` merge、48dp 触控目标、字体缩放                             | 触控目标下限 48dp                                            |
| [compose/testing.md](./compose/testing.md)                       | `runTest` + Turbine、ViewModel 测试模板、CI 红线                                             | ViewModel 单测覆盖率红线                                     |
| [compose/migration-review.md](./compose/migration-review.md)     | View → Compose 三阶段迁移、14 项机器/人工 Review Checklist、典型违规示例                     | 机器项 CI 强制，人工项 Review 打回                           |

## 二、领域 × 规范 覆盖矩阵

> "缺" 表示该领域**尚无项目级规范**，写相关代码前需先补规范或走人工评审，不要自由发挥。

| 领域                                                 | 规范状态                                                                        |
| ---------------------------------------------------- | ------------------------------------------------------------------------------- |
| 协程 / 异步                                          | ✅ coroutine-rules.md                                                           |
| Room 数据层                                          | ✅ repository-rules.md                                                          |
| Compose UI（结构/状态/性能/导航/可访问性/测试/迁移） | ✅ compose/ 共 8 文件                                                           |
| JS 规则引擎（Rhino，书源规则执行）                   | ❌ 缺：线程模型、超时、JS 与 Java 交互的异常边界                                |
| 网络层（Cronet / OkHttp 双栈）                       | ❌ 缺：双栈选择策略、超时/重试、响应解析线程                                    |
| 全局单例（`ReadBook` / `CacheBook` / `AudioPlay`）   | ❌ 缺：线程安全、生命周期、状态持有红线                                         |
| 文件存储 / IO（书目录、缓存目录）                    | ❌ 缺：原子写、IO 线程、文件句柄释放                                            |
| 内嵌服务（NanoHTTPD / WebDAV）                       | ❌ 缺：端口、鉴权、安全边界                                                     |
| Web 服务验证（E2E 三层）                             | ✅ e2e-testing-rules.md（操作手册：docs/architecture/Web服务端到端测试方法.md） |
| View / Compose 混用                                  | ⚠️ 部分覆盖（migration-review.md 只讲迁移方向，不讲长期混用边界）               |
| 事件双轨（LiveEventBus vs `Channel<Event>`）         | ✅ live-event-bus-rules.md（§3 双轨选型裁决规则）                               |
| 后台任务 / 服务（WorkManager / ForegroundService）   | ⚠️ 仅一句话（coroutine-rules.md 规则 3），无独立规范                            |
| 日志与 PII 脱敏                                      | ❌ 缺                                                                           |
| 对外发布日志（updateLog.md）                         | ✅ update-log-rules.md                                                          |
| API 兼容 / minSdk 23 红线                            | ✅ api-compat-rules.md                                                          |
| Intent/Bundle 跨组件传大载荷                         | ✅ intent-payload-rules.md（Binder 事务超限红线）                               |

## 三、跨文件规则速查（高频冲突点，已对齐）

| 问题                                   | 结论                                                                                                                                                                         | 出处                                                  |
| -------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------- |
| View 系一次性任务用什么？              | `execute` 链式（`BaseViewModel`/`BaseActivity` 等宿主）                                                                                                                      | coroutine-rules.md §2.1                               |
| Compose ViewModel 一次性增删改用什么？ | `viewModelScope.launch` + `try-catch`（更新 UiState + 抛 Event）。Compose VM 不继承 `BaseViewModel`，`execute` 不可用；且 `execute` 回调链对快速完成的任务存在不触发的时序坑 | coroutine-rules.md §2.1 例外 + state-events.md §4.1.1 |
| Flow 在哪层暴露、哪层消费？            | Repository 暴露 `Flow`，ViewModel 用 `stateIn(viewModelScope, ...)` 收敛；Screen 用 `collectAsStateWithLifecycle()`                                                          | coroutine-rules.md §3 + state-events.md §4.1/§4.2     |
| 一次性事件放 StateFlow 吗？            | 不放。View 侧走 `LiveEventBus`，Compose 侧走 `Channel<Event>`（UNLIMITED / CONFLATED 分档）                                                                                  | coroutine-rules.md §3 + state-events.md §4.1          |
| 事件消费侧绑定到哪？                   | `repeatOnLifecycle(STARTED)`，禁止 Application scope 常驻 collect                                                                                                            | state-events.md §4.4                                  |
| Dialog 注册成导航路由吗？              | 不注册。UiState 条件渲染 + 显式 `OnBackPressedCallback`                                                                                                                      | state-events.md §4.5                                  |
| 页面内事件外溢成全局事件吗？           | 不外溢。单页面产生+消费 → `Channel<Event>`；跨组件（Service/JS/Receiver 发）→ LiveEventBus。Compose ViewModel 禁止 `postEvent`，Compose Screen 禁止订阅 LiveEventBus         | live-event-bus-rules.md §3                            |
| 高版本 API 怎么调？                    | `Build.VERSION.SDK_INT` 分支（低版本必须有兜底/降级/报错三选一）；禁止 `@SuppressLint("NewApi")` 绕 lint、禁止新增 `tools:overrideLibrary`                                   | api-compat-rules.md §2/§4                             |

## 四、维护约定

1. **改代码改行为，必须同步改对应规范文件**；行为变了规范没变，等于规范是骗人的。
2. 新增规范文件后**必须登记到本索引**，不允许文件躺在目录里没人知道。
3. 规则之间出现矛盾时，以本文件 §三速查表为最终裁决；速查表没覆盖的，提交 PR 讨论后先写进速查表再改规则。
4. 补全优先级（缺项按此顺序补）：
   - **P0**（线上事故高发区，优先）：JS 引擎、网络层、全局单例、文件存储
   - **P1**：内嵌服务安全、View/Compose 长期混用边界
   - **P2**：后台任务服务、日志与 PII
