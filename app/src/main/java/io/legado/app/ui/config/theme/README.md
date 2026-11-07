# theme/ — 主题模块

本目录是应用主题功能的根目录，包含主题管理的全部代码。

## 目录结构

| 子目录 | 用途 |
|--------|------|
| `manage/` | **主题列表**（Jetpack Compose 实现）。单套主题的增删改查、多选、导入导出。 |
| `manage/components/` | 主题列表专用的 Compose UI 组件（主题卡片与预览、编辑弹窗）。 |
| `application/` | **应用主题**（Compose 实现）。把日/夜主题 + 顶栏 + 底栏 + 封面图集打包成一套方案。 |
| `application/edit/` | 应用主题的编辑页（Compose 表单：名称 + 八个组件选择）。 |
| `legacy/` | 旧版入口，仅剩 `ThemeConfigFragment`（PreferenceFragment，主题设置总入口）。 |

## 架构概览

```
theme/
├── manage/              # 主题列表（Compose）
│   ├── ThemeManageActivity.kt
│   ├── ThemeManageScreen.kt
│   ├── ThemeManageViewModel.kt
│   ├── ThemeManageUiState.kt
│   └── components/
│       ├── ThemeCard.kt              # 主题卡片 + 预览
│       ├── ThemePreview.kt           # 主题配色预览块
│       ├── ThemeBackgroundImage.kt   # 背景图处理
│       └── ThemeEditDialog.kt        # 编辑弹窗
│
├── application/         # 应用主题（Compose）
│   ├── ApplicationThemeActivity.kt
│   ├── ApplicationThemeScreen.kt
│   ├── ApplicationThemeViewModel.kt
│   ├── edit/                         # 编辑页
│   │   ├── ApplicationThemeEditActivity.kt
│   │   ├── ApplicationThemeEditScreen.kt
│   │   └── ApplicationThemeEditViewModel.kt
│   └── components/
│       ├── ApplicationThemePreview.kt        # 列表配色预览
│       └── ThemeComponentOptionsDialog.kt    # 导入/删除的组件勾选
│
└── legacy/
    └── ThemeConfigFragment.kt        # PreferenceFragment 配置入口
```

## 共用组件

主题列表与应用主题共用 `ui/config/widget/` 下的管理页积木：
`ConfigManageScaffold`、`ConfigManageCard`、`ConfigAddBottomBar`、`ConfigMultiSelectTextAction`、
`ConfigItemMoreButton`、`ConfigNameDialog`、`ConfigEditDialogFrame`、`ConfigSliderDialog`、
`ConfigDialogRow`、`DayNightPager`。

## 外部依赖

- 主题配置数据层：`io.legado.app.help.config.ThemeConfig`
- 应用主题管理器：`io.legado.app.help.config.ApplicationThemeManager`
