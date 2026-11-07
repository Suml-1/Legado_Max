# widget/ — 配置管理通用零件（Compose）

所有配置管理页（主题列表、应用主题、顶栏、底栏、气泡）共享的通用 UI 状态和 Composable 组件，
采用组合模式而非继承模式实现复用。

## 文件说明

| 文件 | 用途 |
|------|------|
| `ConfigManageState.kt` | 通用状态 Holder，封装日/夜 Tab、多选模式、编辑弹窗可见性等 UI 交互状态；`rememberSharedConfigManageState()` 让主题列表/底栏/顶栏共用同一份日/夜 Tab 选择。 |
| `ConfigManageScaffold.kt` | Scaffold + TopAppBar 骨架，含返回行为、多选/普通模式 actions 插槽；多选时标题显示「已选择 N 项」并隐藏底栏。 |
| `ConfigManageCard.kt` | 统一配置卡片：前置预览位 + 名称 + 内置/已应用胶囊 + 摘要 + 操作行 + 多选勾选。 |
| `ConfigItemMenu.kt` | 卡片上的「更多」按钮与下拉菜单（`ConfigItemMoreButton`）、顶部栏图标菜单（`ConfigActionIconMenu`）、卡片操作文字按钮（`ConfigActionButton`）。 |
| `ConfigAddBottomBar.kt` | 底部「新建/添加」按钮，五个管理页共用。 |
| `DayNightPager.kt` | 日/夜分页管理器，封装 Tab + Pager 双向联动、摘要文本、空状态，以及通用 `ConfigList`。 |
| `ConfigMultiSelectActions.kt` | 顶栏多选文字操作项（`ConfigMultiSelectTextAction`，沿用旧版把批量操作排在顶栏的做法）与从剪贴板导入图标按钮。 |
| `ConfigEditDialogFrame.kt` | 编辑弹窗统一外框：标题 + 限高可滚动内容（带滚动块）+ 取消/保存。 |
| `ConfigDialogRow.kt` | 弹窗内的「标题 + 当前值（+ 颜色块）」行。 |
| `ConfigNameDialog.kt` | 名称输入弹窗（名称为空时禁用确认）。 |
| `ConfigSliderDialog.kt` | 百分比/整数滑块弹窗，可自定义取值文案（如圆角 1.0、气泡缩放 1.2）。 |
| `ConfigUrlImportDialog.kt` | 在线导入地址弹窗，带最近地址列表。 |
| `SegmentedTabRow.kt` | 泛型胶囊分段 Tab 行（滑动指示器版），视觉态由连续 `progress: Float` 驱动，滑块随 Slider 逐帧跟随滑动，文字/图标激活色过半切换，选中色跟随应用主色调。 |

## 架构模式

- **组合模式**：每个管理页通过 `rememberConfigManageState()` 持有通用状态，
  用自己的 Screen 函数组装通用零件 + 专属卡片/弹窗，无需继承任何基类。
- **状态与数据分离**：通用状态（Tab/多选/编辑弹窗）在 Composable 层由 `ConfigManageState` 管理，
  数据加载/增删改查由各页面 ViewModel 自行负责。
- **事件转发**：一次性事件（Toast、分享、文件导出）由各 ViewModel 通过 `Channel` 向上抛给 Activity。
- **统一操作口径**：导入收在顶栏（图标菜单或图标按钮），新建收在底部按钮；
  单条「更多」与长按多选都提供「置顶」；「已应用」统一为标题右侧胶囊标签。
- **多选沿用旧版形态**：长按进入多选后，标题变「已选择 N 项」，全选/置顶/导出/删除
  以**文字**排在顶栏右侧（不是底栏图标），与改造前的 View 版管理页一致。

## 与具体管理页的关系

```
widget/（通用零件）          theme/manage/、theme/application/、navigationbar/、topbar/、bubble/
────────────────────         ─────────────────────────────────────────────────────────────
ConfigManageState      ←──   rememberConfigManageState() / rememberSharedConfigManageState()
ConfigManageScaffold   ←──   组装 Screen
ConfigManageCard       ←──   传入 leading / actions / 多选状态
ConfigItemMoreButton   ←──   传入 ConfigMenuItem 列表
ConfigAddBottomBar     ←──   底部新建
DayNightPager          ←──   传入 dayContent / nightContent（无日夜维度的页面不用）
多选文字操作            ←──   通过 scaffold 的 multiSelectActions 传入
ConfigList             ←──   渲染卡片并挂 VerticalScrollbar
```

新增配置管理页时：

1. 创建专属 ViewModel（仅数据操作，不含 UI 状态）
2. 创建专属 EditDialog 与（必要时）预览组件
3. 在 Screen 中组装上述通用零件 + 专属组件
