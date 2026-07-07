# Prototype Decision Record

## Question

这个 app 的 UI 应该长什么样？（在「清新简约」设计约束下，选一个视觉方向）

## Method

Web HTML/CSS 单文件 prototype，3 个视觉方向（结构/信息层级/主 affordance 都不同），通过 `?variant=` + 浮动底栏切换。4 屏画面对比：欢迎页 / 日程日视图 / 子项目列表+任务详情 / 回顾。

文件：`prototype/ui-variants.html`（已删除，决策记录在此）。

## Variants

| Variant | 名称 | 方向 |
|---|---|---|
| A | 薄荷微风 | 薄荷绿 · 圆角大卡片 + 柔和阴影 · 强调色用于 CTA 和已完成项 |
| B | 雾蓝线条 | 雾蓝 · 完全扁平、用发际线分隔 · 文字优先 · 无彩色强调 |
| C | 暖沙留白 | 暖沙底色 · 编辑式大标题（衬线体）· 极致留白 · 赤陶色作强调 |

## Decision

**Variant A「薄荷微风」胜出。**

## Implications for Android 实现

- **主色**：薄荷绿（推荐色值 `#5BBF9E`）作为全局强调色，用于 CTA 按钮、已完成状态、当前时间指示线
- **底色**：大面积白色 / 极浅灰绿（`#F8FCFA`、`#FAFBFD`）
- **卡片**：圆角大卡片（~18px radius）+ 柔和阴影
- **排版**：大留白、大字号、卡片大量留空
- **动效**：克制，只用淡入淡出 / 微小位移
- **Tab bar**：白色底，active tab 用薄荷绿背景胶囊标识
- **子项目状态标签**：pill 形状，已完成=薄荷绿、已排期=淡紫蓝、backlog=浅灰
- **字体**：系统无衬线（PingFang SC / Roboto）

Prototype 文件已删除。实现时参考本 NOTES.md 中的色值与组件风格。
