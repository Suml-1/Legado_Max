# 构建命令与工程配置参考

> 本文档是构建体系的**工程配置参考**（不是强制红线）：构建命令全表、Web 前端命令、Build Variants、SDK/JDK、版本目录。
> **什么时候读**：构建报错、遇到不认识的 flavor / 命令、首次构建、涉及 `gradle/libs.versions.toml` 或 `modules/web/` 时。
> 日常最常用的 6 条命令已放在 CLAUDE.md，这里记录完整矩阵。

## 1. Gradle 构建命令

Gradle wrapper（Windows 下为 `gradlew.bat`），JDK 17 要求。

| 命令                                            | 说明                                                                                                                                                            |
| ----------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `./gradlew assembleDebug`                       | Debug 构建（默认 flavor：appMax）                                                                                                                               |
| `./gradlew assembleRelease`                     | Release 构建（ProGuard + resource shrinking）                                                                                                                   |
| `./gradlew assembleAppMaxDebug`                 | appMax（`io.legado.app.yuedu`，共存包）                                                                                                                         |
| `./gradlew assembleAppLegacyRelease`            | appLegacy（`io.legado.app`，与原版一致）                                                                                                                        |
| `./gradlew assembleAppSDebug`                   | appS（`io.legado.app.yuedu.a`）                                                                                                                                 |
| `./gradlew installDebug` / `installAppMaxDebug` | 安装到设备                                                                                                                                                      |
| `./gradlew test`                                | 单元测试                                                                                                                                                        |
| `./gradlew connectedAndroidTest`                | 仪器测试（Instrumented tests）                                                                                                                                  |
| `./gradlew clean`                               | 清理各模块 `build/` 产物（含 `build/intermediates/lint-cache`，会打断 lint/编译增量缓存；仅在构建产物污染、切 flavor 后异常、或 CI 收尾时用，日常迭代别轻易跑） |
| `./gradlew stop`                                | 停止 Gradle daemon                                                                                                                                              |
| `./gradlew.bat :app:compileAppMaxDebugKotlin`   | 语法检查式编译（"Grammar Test"）                                                                                                                                |
| `./gradlew lint`                                | Android Lint（CI 实际入口为 `:app:lintAppMaxDebug` 单变体）。**迭代期用 IDE 内联 lint，CLI 全量作收尾/CI 门禁**；范围自适应见下节                               |
| `./gradlew spotlessCheck`                       | Kotlin 格式检查：只检查自 origin/main 以来的改动（CI 为可选警示，continue-on-error 不阻塞）                                                                     |
| `./gradlew spotlessApply`                       | 自动修正全部 Kotlin 格式问题；提交前可手动执行                                                                                                                  |
| `./gradlew app:downloadCronet`                  | **首次构建前必须执行**，下载 Cronet 原生库                                                                                                                      |
| `./gradlew assembleDebug --warning-mode all`    | 查看 DSL 语法警告（Windows/Mac/Linux 同命令）                                                                                                                   |

### Android Lint 门禁与提速

- **定位**：lint 是本地最后一道门禁（验证顺序：单测 → lint），CI 通过 `.github/workflows/lint.yaml` 跑 `:app:lintAppMaxDebug` 单变体；`abortOnError` 生效——只要出现 **baseline 之外的新增 error** 就 `BUILD FAILED`。
- **存量基线**：`app/lint-baseline.xml` 收录历史问题，lint 只拦新增；存量修复后基线对应项需 `./gradlew :app:lintAppMaxDebug -DupdateBaseline=true`（或 IDE）刷新，勿手改。
- **范围自适应**（配置在 `app/build.gradle` 的 `lintResolveScope()`，按 git 改动路径自动决定，详见 [docs/architecture/android-lint-门禁与提速.md](../architecture/android-lint-门禁与提速.md)）：
  - 只改 `app/src/main/**` → 快跑（关 `checkDependencies` + `checkTestSources`）；触及 `modules/*`、`**/src/test`、`**/src/androidTest`、任意 `build.gradle`、`lint-baseline.xml` 等 → 全量。
  - CI（env `CI`/`GITHUB_ACTIONS`）**永远全量**，门禁不降级。
  - 手动覆盖：`-PlintFast` 强制快跑 / `-PlintFull` 强制全量 / `-PlintBase=<ref>` 指定比较基线（默认 `origin/main`，首次用前需 `git fetch origin main`）。
- **提速真相**：本项目耗时大头是 app 主源码自身分析（单次冷跑约 7 分钟），范围裁剪实测几乎不减墙钟时间。真正有效的做法：**迭代期靠 IDE 内联 lint（按文件增量、改哪查哪）**，别在改代码循环里反复跑 CLI；CLI 全量只在 push 前 / CI 执行。跑 CLI 时尽量保持 daemon 与 `build/intermediates/lint-cache` 存活（反复 `--stop` / `clean` 会打断增量）。

### Kotlin 代码格式（spotless + ktlint）

- 配置位于根 `build.gradle` 的 `spotless {}` 块；`ratchetFrom 'origin/main'` 使检查只覆盖**自 origin/main 以来的改动**，存量代码不强制全量合规。
- **前提**：本地首次使用前需先 `git fetch origin main`（让 `origin/main` ref 存在），否则 `spotlessCheck` 会因找不到基线而报错；CI 通过 `fetch-depth: 0` 满足该前提。
- 与存量惯例冲突的风格类规则已在根 build.gradle 的 `editorConfigOverride` 中显式关闭（完整清单以该配置块为准）：函数/属性命名（Compose 大写组件名、驼峰常量）、import 字母序、wildcard 导入、注释位置类规则、行宽（暂放开）等。需要调整时改根 build.gradle 的 `editorConfigOverride`。
- Kotlin 格式化由 spotless 负责；`prettier`（node）按 package.json `lint-staged` 实配只处理 `js/ts/jsx/tsx/vue/md`，**不碰 `.java` 与 `.kt`**。

## Web 前端（modules/web）

嵌入 HTTP 服务器的前端是 Vue 3 + Vite 应用，构建产物同步到 `app/src/main/assets/web/vue/`。

```bash
cd modules/web
pnpm install        # requires Node >= 20, pnpm >= 9
pnpm dev            # local dev server with HMR
pnpm build          # production build + syncs to assets/web/vue/
pnpm lint:fix       # eslint auto-fix
pnpm format         # prettier
```

## Build Variants（3 个 flavor）

product flavors 维度为 "app"：

| flavor      | 包名                    | 说明                     |
| ----------- | ----------------------- | ------------------------ |
| `appLegacy` | `io.legado.app`         | 与原版 Legado 一致       |
| `appMax`    | `io.legado.app.yuedu`   | 共存包，**主要开发目标** |
| `appS`      | `io.legado.app.yuedu.a` | 另一个共存包             |

- SDK 级别：minSdk 23 / targetSdk 37 / compileSdk 37 / JVM 17 toolchain。
- `coreLibraryDesugaring` 开启 —— JVM 17 语法（records、text blocks、List.of）可兼容到 API 23。
- debug/release 两种构建类型均追加 `applicationIdSuffix`（`.debug` / `.release`），所以安装包如 `io.legado.app.yuedu.debug`，不是裸 flavor id。
- Release：`minifyEnabled` + `shrinkResources` + ProGuard（`app/proguard-rules.pro`、`app/cronet-proguard-rules.pro`）；Debug：不混淆。

## 版本与 SDK

- 所有依赖版本统一在 `gradle/libs.versions.toml`，在 `build.gradle.kts` / `build.gradle` 中按 `libs.xxx` 引用，禁止硬编码版本号。
- 主版本速览：Kotlin 2.3.10、Hilt 2.59、OkHttp 5.3.2、Room 2.8.4、Coroutines 1.10.2、Compose BOM 2026.08.00、JS 引擎 HtmlUnit core-js 5.3.0-legado.4-android21.1（`third_party/maven` 离线产物，换版本时须同步 `modules/rhino/consumer-rules.pro` 的 keep 包名）。
- 新增依赖时同步更新本目录与相关规则文档（见 project-rules README 索引）。
