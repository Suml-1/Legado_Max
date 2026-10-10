# [English](English.md) [中文](README.md)

![icon_android](https://gitee.com/lyc486/yuedu/raw/master/icon_android.png)
<a href="https://jb.gg/OpenSourceSupport" target="_blank">
<img width="24" height="24" src="https://resources.jetbrains.com/storage/products/company/brand/logos/jb_beam.svg?_gl=1*135yekd*_ga*OTY4Mjg4NDYzLjE2Mzk0NTE3MzQ.*_ga_9J976DJZ68*MTY2OTE2MzM5Ny4xMy4wLjE2NjkxNjMzOTcuNjAuMC4w&_ga=2.257292110.451256242.1669085120-968288463.1639451734" alt="idea"/>
</a>

<div align="center">
<img width="125" height="125" src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="legado"/>
<br>
阅读
<br>
<a href="https://loyc.xyz/c/legado.html" target="_blank">软件介绍页</a>
<br>
阅读继承自阅读Sigma，在其基础上新增更多实用和强大功能。
</div>

## ⚠️ 重要声明

**关于本分支的说明**

1. **开发背景**：本分支由开发者在 AI 编程工具的辅助下完成。

2. **开发者资质**：开发者非计算机专业背景，未系统学习过 Kotlin、Java 等编程语言及计算机组成原理等基础学科。

3. **代码质量**：鉴于上述背景，代码在架构设计、规范性和性能优化方面可能存在不足，请知悉。

4. **使用须知**：
   - 本项目为开发者个人自用阅读器，开源仅供学习与技术交流。
   - 开发者不对任何用户的使用体验、数据安全或设备兼容性作任何形式的保证或承诺。
   - 如您对代码质量有较高要求，或介意上述情况，建议谨慎评估后再决定是否使用。

## 版本说明

- 本项目基于 [lyc版](https://gitee.com/lyc486/legado) 持续开发，在此基础上有更多实用和强大功能
- **appLegacy版**：包名 `io.legado.app`，与原版相同，可覆盖更新
- **appMax版**：包名 `io.legado.app.yuedu`，共存包名，不会覆盖原版
- **appS版**：包名 `io.legado.app.yuedu.a`，另一个共存包名

## 参与贡献 / 安全

- 想提 PR / 了解开发环境与提交规范：见 [CONTRIBUTING.md](CONTRIBUTING.md)。
- 本工程规则、架构、构建命令的总纲（同时是给 AI 的项目契约）见 [CLAUDE.md](CLAUDE.md)，强制编码规范库见 [`docs/project-rules/`](docs/project-rules/README.md)。

# 覆盖安装注意事项 [![](https://img.shields.io/badge/-覆盖安装注意事项-F5F5F5.svg)](#覆盖安装注意事项-)

> ⚠️ **重要提醒**：安装Max版前一定要备份！

<details>
<summary>从其他版本覆盖安装需要注意的事项</summary>

**1. MD3版本书架分组兼容性问题**

从MD3版本备份的书架分组在Max版本中无法正常显示。虽然在"分组管理"界面可以看到这些分组，但在其他界面无法显示。这是因为MD3版本自行修改了分组相关的备份数据格式，导致其他版本的阅读无法正常加载这些分组数据。

**2. 跨很多的版本升级可能导致数据丢失**

从lyc版本（包括Sigma和Plus版）直接安装最新的Max版可能会出现书架和其他数据全部丢失的情况。这是因为Max版最近更新幅度非常大，跨越了太多版本，数据格式可能存在兼容性问题。

⚠️ 此时使用WebDAV恢复和本地恢复都无法正常工作！

**解决方案**：

- 将软件完全卸载后重新安装
- 再进行数据恢复操作，此时即可正常恢复

</details>

<a href="#readme">
    <img src="https://img.shields.io/badge/-返回顶部-orange.svg" alt="#" align="right">
</a>

[![](https://img.shields.io/badge/-Contents:-696969.svg)](#contents) [![](https://img.shields.io/badge/-Max特色-F5F5F5.svg)](#Max版特色功能-) [![](https://img.shields.io/badge/-Function-F5F5F5.svg)](#Function-主要功能-) [![](https://img.shields.io/badge/-Community-F5F5F5.svg)](#Community-交流社区-) [![](https://img.shields.io/badge/-API-F5F5F5.svg)](#API-) [![](https://img.shields.io/badge/-Other-F5F5F5.svg)](#Other-其他-) [![](https://img.shields.io/badge/-Grateful-F5F5F5.svg)](#Grateful-感谢-) [![](https://img.shields.io/badge/-Interface-F5F5F5.svg)](#Interface-界面-) [![](https://img.shields.io/badge/-覆盖安装注意事项-F5F5F5.svg)](#覆盖安装注意事项-)

# Max版特色功能 [![](https://img.shields.io/badge/-Max特色-F5F5F5.svg)](#Max版特色功能-)

阅读Max在继承原版所有功能的基础上，新增以下特色功能：

📖 **阅读体验增强** — 翻页速度自由调节 · 页眉页脚字体独立调节 · 波浪线/虚线下划线 <br>
🔧 **调试与开发工具** — 集成调试工具台 · 代码编辑器增强 · TTS源调试 · 字典/字重调试界面 · 调试日志悬浮球 · 书源检测新界面 · 代码编辑切换规则 · 查询规则快速跳转 · 帮助文档全局搜索 · 书签搜索<br>
📊 **数据与记录管理** — 阅读记录四种视图 · 热力图日历 · URL记录 · 完全备份支持 · 缓存管理界面增强 · 阅读记录搜索<br>
🎨 **UI/UX优化** — HTML封面模板引擎 · 主题批量操作 · 应用主题管理（一键切换日/夜间主题+顶栏+底栏+封面图集） · 底栏管理（布局模式/效果/自定义图标） · 顶栏管理（样式/壁纸/圆角/标签配色） · 段评气泡管理（SVG模板/日夜间配色） · 封面图集 · 帮助文档选择器 · 帮助文档全局搜索 · 发现页useWeb展示 · 主题置顶功能 · 备份文件验证 · 备份文件验证<br>
🚀 **功能扩展增强** — @webjs规则类型 · 智能导入 · 自动更新 · JavaScript增强函数 · 向前向后预下载调节 · JS Packages使用指南

<a href="#readme">
    <img src="https://img.shields.io/badge/-返回顶部-orange.svg" alt="#" align="right">
</a>

# Function-主要功能 [![](https://img.shields.io/badge/-Function-F5F5F5.svg)](#Function-主要功能-)

[English](English.md)

<details><summary>中文</summary>
1.自定义书源，自己设置规则，抓取网页数据，规则简单易懂，软件内有规则说明。<br>
2.列表书架，网格书架自由切换。<br>
3.书源规则支持搜索及发现，所有找书看书功能全部自定义，找书更方便。<br>
4.订阅内容,可以订阅想看的任何内容,看你想看<br>
5.支持替换净化，去除广告替换内容很方便。<br>
6.支持本地TXT、EPUB阅读，手动浏览，智能扫描。<br>
7.支持高度自定义阅读界面，切换字体、颜色、背景、行距、段距、加粗、简繁转换等。<br>
8.支持多种翻页模式，覆盖、仿真、滑动、滚动等。<br>
9.缺失的功能全部加上<br>
10.软件开源，持续优化，无广告。
</details>

<a href="#readme">
    <img src="https://img.shields.io/badge/-返回顶部-orange.svg" alt="#" align="right">
</a>

# Community-交流社区 [![](https://img.shields.io/badge/-Community-F5F5F5.svg)](#Community-交流社区-)

#### Discord

[![Discord](https://img.shields.io/discord/560731361414086666?color=%235865f2&label=Discord)](https://discord.gg/VtUfRyzRXn)

#### Other

https://www.yuque.com/legado/wiki/community

<a href="#readme">
    <img src="https://img.shields.io/badge/-返回顶部-orange.svg" alt="#" align="right">
</a>

# API [![](https://img.shields.io/badge/-API-F5F5F5.svg)](#API-)

- 阅读3.0 提供了2种方式的API：`Web方式`和`Content Provider方式`。您可以在[这里](api.md)根据需要自行调用。
- 可通过url唤起阅读进行一键导入,url格式: legado://import/{path}?src={url}
- path类型: bookSource,rssSource,replaceRule,textTocRule,httpTTS,theme,readConfig,dictRule,[addToBookshelf](/app/src/main/java/io/legado/app/ui/association/AddToBookshelfDialog.kt)
- path类型解释: 书源,订阅源,替换规则,本地txt小说目录规则,在线朗读引擎,主题,阅读排版,添加到书架

<a href="#readme">
    <img src="https://img.shields.io/badge/-返回顶部-orange.svg" alt="#" align="right">
</a>

# Other-其他 [![](https://img.shields.io/badge/-Other-F5F5F5.svg)](#Other-其他-)

##### 免责声明

https://gedoor.github.io/Disclaimer

##### 阅读3.0

- [书源规则](https://mgz0227.github.io/The-tutorial-of-Legado/)
- [更新日志](/app/src/main/assets/updateLog.md)
- [帮助文档](/app/src/main/assets/web/help/md/appHelp.md)
- [web端书架](https://github.com/gedoor/legado_web_bookshelf)
- [web端源编辑](https://github.com/gedoor/legado_web_source_editor)

<a href="#readme">
    <img src="https://img.shields.io/badge/-返回顶部-orange.svg" alt="#" align="right">
</a>

# Grateful-感谢 [![](https://img.shields.io/badge/-Grateful-F5F5F5.svg)](#Grateful-感谢-)

> - org.jsoup:jsoup
> - cn.wanghaomiao:JsoupXpath
> - com.jayway.jsonpath:json-path
> - com.github.gedoor:rhino-android
> - com.squareup.okhttp3:okhttp
> - com.github.bumptech.glide:glide
> - org.nanohttpd:nanohttpd
> - org.nanohttpd:nanohttpd-websocket
> - cn.bingoogolapple:bga-qrcode-zxing
> - com.jaredrummler:colorpicker
> - org.apache.commons:commons-text
> - io.noties.markwon:core
> - io.noties.markwon:image-glide
> - com.hankcs:hanlp
> - com.positiondev.epublib:epublib-core
> - com.github.Moriafly:LyricViewX
> - io.github.rosemoe:editor
