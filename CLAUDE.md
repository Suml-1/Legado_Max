# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

阅读Max (legado_Plus) — an Android e-book reader app forked from Legado. Supports custom book sources with user-defined rules (Jsoup selectors + Rhino JS), RSS subscriptions, local TXT/EPUB reading, and an embedded HTTP/WebSocket server for remote control.

## Build Commands

Uses Gradle wrapper (`gradlew.bat` on Windows). JDK 17 required.

```bash
# Debug build (default flavor: appMax)
./gradlew assembleDebug

# Release build (ProGuard + resource shrinking enabled)
./gradlew assembleRelease

# Specific flavor builds
./gradlew assembleAppMaxDebug       # appMax (io.legado.app.yuedu, coexistence)
./gradlew assembleAppLegacyRelease  # appLegacy (io.legado.app, same as original)
./gradlew assembleAppSDebug         # appS (io.legado.app.yuedu.a)

# Install to device
./gradlew installDebug
./gradlew installAppMaxDebug

# Tests
./gradlew test                      # Unit tests
./gradlew connectedAndroidTest      # Instrumented tests

# stop
./gradlew stop

# Grammar Test
./gradlew.bat :app:compileAppMaxDebugKotlin

# Lint
./gradlew lint

# Download Cronet native libs (required before first build)
./gradlew app:downloadCronet

# 查看DSL语法警告
# Windows
gradlew assembleDebug --warning-mode all
# Mac/Linux
./gradlew assembleDebug --warning-mode all
```

### Web Frontend (modules/web)

The embedded HTTP server's frontend is a Vue 3 + Vite app in `modules/web/`. It builds to `app/src/main/assets/web/vue/`.

```bash
cd modules/web
pnpm install        # requires Node >= 20, pnpm >= 9
pnpm dev            # local dev server with HMR
pnpm build          # production build + syncs to assets/web/vue/
pnpm lint:fix       # eslint auto-fix
pnpm format         # prettier
```

## Architecture

MVVM pattern with AndroidViewModel + ViewBinding + Coroutines.

### Base Classes (`io.legado.app.base`)

- `BaseActivity<VB>` — all Activities extend this. Manages theming, system bars, view binding. Override `observeLiveBus()` for event subscriptions (auto-cleaned on destroy).
- `VMBaseActivity<VB, VM>` — adds abstract `viewModel` property.
- `BaseViewModel` — extends `AndroidViewModel`. Key method: `execute { }` returns a `Coroutine<T>` with chainable `.onSuccess`, `.onError`, `.onFinally`. Default context is `Dispatchers.IO`, callbacks on `Dispatchers.Main`.

### Key Patterns

- **Coroutine helper**: `BaseViewModel.execute()` wraps `Coroutine.async()`. Use this instead of raw `viewModelScope.launch`.
- **Event bus**: `LiveEventBus` for cross-component events. Subscribe via `observeEvent<T>(key) { ... }` in `observeLiveBus()`.
- **Database**: Room (`AppDatabase` v100), singleton at `appDb`. DAOs in `data/`, entities in `data/entities/`. Uses KSP (not kapt).
- **Book source rules**: Rhino JS engine (`:modules:rhino` module) evaluates user-defined rules. The `analyzeRule` package in `model/` handles rule parsing.
- **Singletons in model/**: `ReadBook`, `CacheBook`, `AudioPlay` manage global reading state.
- **Config packages**: `TopBarConfig` and `BubblePackageManager` store configs as file system directories (JSON + assets like wallpapers/icons), not SharedPreferences. `NavigationBarConfig` uses SharedPreferences. `ApplicationThemeManager` combines all sub-configs into exportable/importable theme packages (zip).

### Modules

The project has three library modules in `modules/`:

- `modules/book` — fork of epublib (EPUB parsing), package `me.ag2s.epublib`
- `modules/rhino` — fork of Mozilla Rhino JS engine, package `com.script`. Evaluates user-defined book source rules at runtime.
- `modules/web` — Vue 3 frontend for the embedded HTTP/WebSocket server (see above)

### Source Layout

`app/src/main/java/io/legado/app/`:
- `ui/` — Activities/Fragments grouped by feature (book/, rss/, source/, config/, debuglog/, image/)
- `model/` — domain logic (WebBook for HTTP fetching, analyzeRule for rule engine, ParagraphBubbleRenderer, BookCover)
- `data/` — Room DB, DAOs, repositories
- `help/` — helpers (config managers for theme/navbar/topbar/bubble, http client, coroutine utilities, source management)
- `lib/theme/` — theme utilities (accent colors, typography, corners, page colors, TitleBar config extensions)
- `utils/` — Kotlin extensions (~100+ files)
- `web/` — embedded NanoHTTPD server + WebSocket endpoints

### Compose Usage

Jetpack Compose (Material3, BOM 2025.04.01) is used for newer UI surfaces (e.g. debug log panel). Traditional View system (ViewBinding + XML layouts) is used for most existing screens. Both coexist — ComposeViews can be overlaid on View-based Activities.


### UI 架构规范（必须遵守）

`io.legado.app.ui` 包下所有 Compose 相关代码，必须遵循 `docs/project-rules/UI-ARCHITECTURE.md`。写任何 UI 代码前先读该文档，核心要点：

- 目录结构：新 Compose 通用组件进 `ui/widget/components/`，禁止在 `ui/widget/` 根目录（XML 存量混存区）继续堆放；Feature 私有组件归集到 Feature 内 `components/`，禁止跨 Feature 引用
- 命名：`*Screen.kt` / `*ViewModel.kt` / `*Repository.kt` / `*UiState.kt`；禁止 `*View.kt` 用于 Compose 代码，禁止 `*Components.kt` 大杂烩文件
- Composable API：`modifier` 永远是第一个参数；回调用 `onXxx` DSL 命名
- 禁止在 Screen 文件内定义 `private fun` 形式的可复用组件
- 老代码迁移期允许 `@Suppress("LegadoUiViolation")` + TODO 过渡，违规项见该文档 §14 Code Review Checklist（机器硬卡项 CI 会直接失败）

## Coding Conventions

- Kotlin 代码风格遵循 Google Android Style Guide
- 命名规则：
  - Activities: `XxxActivity`
  - ViewModels: `XxxViewModel`
  - Fragments: `XxxFragment`
- 日志使用统一的 tag 格式：`AppTag.xxx`

## Dependency Management

- 所有依赖版本通过 `gradle/libs.versions.toml` 统一管理
- 禁止直接在 `build.gradle` 中硬编码版本号
- 新增依赖需同步更新版本目录文档

## Testing Strategy
这个视情况讨论，因为有时开发环境不允许。
- 单元测试：`app/src/test/`
- 集成测试：`app/src/androidTest/`
- 测试覆盖率要求：核心模块 ≥ 80%
- Mock 框架：Mockk
- 协程测试：kotlinx-coroutines-test


## Version Catalog

All dependency versions are in `gradle/libs.versions.toml`. In `build.gradle.kts` or `build.gradle`, reference them as `libs.xxx`. Major versions: OkHttp 5.3.2, Room 2.7.1, Coroutines 1.10.2, Compose BOM 2025.04.01.

## Build Variants

Three product flavors in dimension "app":
- `appLegacy` — same package name as original Legado (`io.legado.app`)
- `appMax` — coexistence package (`io.legado.app.yuedu`), the primary development target
- `appS` — another coexistence package (`io.legado.app.yuedu.a`)

Release builds: minifyEnabled + shrinkResources + ProGuard (`app/proguard-rules.pro`, `app/cronet-proguard-rules.pro`). Debug builds: no minification.

## CI/CD

GitHub Actions in `.github/workflows/`:
- `test.yml` — builds all 3 release flavors on push to main; auto-creates GitHub/Gitee releases with changelog from `updateLog.md`
- `web.yml` — builds the Vue frontend on changes to `modules/web/` and commits the output to `app/src/main/assets/web/vue/`
- `cronet.yml` — updates Cronet native libraries

## Conventions

- Annotation processing uses KSP, not kapt.
- `NonTransitiveRClass` is enabled — reference only directly used resources.
- Room schema exports to `$projectDir/schemas` for migration verification.
- Disabled build features: aidl, buildconfig, renderscript, resvalues, shaders.
- Architecture documentation in `Structure/` directory (Chinese) covers app startup flow, database schema, reading flow, event bus, and module dependencies.


## 核心规则

1. **收到任务时，先检查是否有匹配的 skill** — 哪怕只有 1% 的可能性也要检查
2. **设计先于编码** — 收到功能需求时，先用 brainstorming skill 做需求分析
3. **测试先于实现** — 写代码前先写测试（TDD）
4. **验证先于完成** — 声称完成前必须运行验证命令

## Core Rules & Skills

本项目配置了自动化 Skills (位于 `.claude/skills/`) 来辅助开发。Claude 在执行任务时必须遵循以下核心原则：

1.  **Check Skills First**: 开始任务前，必须检查是否有匹配的 Skill。
2.  **Design First**: 编码前必须进行设计分析。
3.  **Test First**: 优先采用 TDD 方式开发。
4.  **Verify Before Finish**: 完成任务必须运行验证命令。

> **注意**：详细的技能列表和触发逻辑请查阅 `.claude/skills/` 目录，或者直接使用 Skill 工具调用。

## 如何使用

当任务匹配某个 skill 时，使用 `Skill` 工具加载对应 skill 并严格遵循其流程。绝不要用 Read 工具读取 SKILL.md 文件。

当任务明确匹配某个 skill 的应用场景时，应调用该 skill 检查。
