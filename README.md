# 知行 Zhixing

**把大任务拆成小项目，排进日程，完成后回顾。**

知行是一款 AI 驱动的本地任务管理 Android 应用。输入一个目标，AI 帮你拆解成可执行的小项目；拖进日程表安排时间；做完后写一段回顾。整个过程纯本地、无账号、无订阅。

<p align="center">
  <a href="https://github.com/Nature-ce/zhixing/releases/latest">⬇️ 下载最新 Release</a>
  &nbsp;·&nbsp;
  <a href="#功能">功能</a>
  &nbsp;·&nbsp;
  <a href="#技术栈">技术栈</a>
  &nbsp;·&nbsp;
  <a href="#构建运行">构建</a>
  &nbsp;·&nbsp;
  <a href="#文档">文档</a>
</p>

---

## 功能

### 🤖 AI 拆解任务
输入任务标题和描述，AI 自动生成一组子项目（含预估时长）。拆解是一次性的，生成后可手动增删改，不再追问 AI。AI 调用走自建后端代理，API Key 不进入客户端（详见 [`docs/adr/0001-ai-via-backend-proxy.md`](docs/adr/0001-ai-via-backend-proxy.md)）。

### 📅 日程排期
- **日视图 + 周视图**：默认打开日视图看"今天做什么"，可切换到周视图看本周全貌
- **拖拽排期**：把 backlog 里的子项目药丸拖进时间格，或长按已排项块拖动改期
- **落点预览幽灵块**：拖动时实时显示半透明幽灵块，精确呈现落点位置与时长；与同任务已排项冲突时变红提示
- **5 分钟精度**：落点按 5 分钟粒度吸附，可排精确时段
- **backlog 面板**：未排期的子项目常驻一侧，支持 inline 编辑名称/预估时长，在同一画面完成"看日程 → 找事 → 拖进去"的闭环

### ✅ 完成与放弃
- 子项目可标记**完成**（绿钩图标）或**放弃**（彻底删除，不写 backlog）
- 任务下所有子项目进入终态 → 任务**自动完成**并解锁回顾
- 逾期块保持"已排期"状态，灰色弱化 + 闹钟图标提示，仍可手动完成/放弃/改期

### 📝 回顾
任务完成后解锁纯文本总结，可反复编辑、随时回看。回顾页含进度图（全任务全貌 + 各子项目完成日期）。

### 🔍 其他
- **全局搜索**：按关键词搜索任务和子项目
- **数据导出**：本地数据导出（JSON / Markdown，MVP 阶段）
- **纯本地存储**：Room 数据库，无账户、无登录、无同步

---

## 技术栈

| 层 | 选型 |
|----|------|
| UI | **Jetpack Compose** + Material 3，单 Activity + Navigation Compose |
| 语言 | Kotlin 2.0.21 |
| 架构 | MVVM（ViewModel + StateFlow），UI 纯展示 |
| 数据库 | Room（SQLite），KSP 编译期生成 |
| 依赖注入 | 手写工厂（ViewModelFactory），无 Hilt |
| 网络 | Retrofit + OkHttp + Moshi，仅 AI 拆解接口 |
| 设计系统 | 自研 token 体系（`LocalZhixingRadii` / `LocalZhixingElevation` / `LocalZhixingStatus` 等 CompositionLocal） |
| 测试 | JUnit4 + AssertJ（单元） / Compose UI Test + MockWebServer（仪器） |
| minSdk / targetSdk | 29 / 35 |
| AGP | 8.7.3 |

### 模块结构

```
app/src/main/java/com/zhixing/
├── ai/                  # AI 拆解编排（TaskDetailViewModel 调用）
├── data/
│   ├── ai/              # Retrofit 服务 + 数据模型
│   ├── dao/             # Room DAO
│   ├── db/              # Room 数据库 + 类型转换
│   ├── entity/          # 实体定义（Task / Subproject / ScheduleItem / Review）
│   └── *                # 领域逻辑（状态机、冲突检测、落点计算）
├── ui/
│   ├── theme/           # 设计系统 token（Color / Spacing / Type / Theme）
│   ├── components/      # 通用组件
│   ├── *Page.kt         # 页面（任务列表、任务详情、日程日/周、回顾、设置）
│   ├── *ViewModel.kt    # 状态持有
│   └── *Composer.kt     # UI 组装 / 列表构造
└── MainActivity.kt      # 单 Activity 入口 + 底部 Tab 导航
```

---

## 构建运行

### 环境要求
- Android Studio Hedgehog (2023.1.1) 或更高
- JDK 17
- Android SDK 35

### 步骤

```bash
# 1. 克隆
git clone https://github.com/Nature-ce/zhixing.git
cd zhixing

# 2. 用 Android Studio 打开，或命令行构建
./gradlew assembleDebug      # debug 包
./gradlew assembleRelease    # release 包（未签名，需自行签名后上架）
```

### AI 配置
AI 拆解需要自建后端代理（详见 ADR-0001）。首次启动进入「设置」页填写：
- **Base URL**：后端服务地址
- **Token**：后端认证令牌
- **Model**：模型名称（默认对接 DeepSeek，OpenAI 兼容接口均可）

配置通过 `local.properties` 或 UI 输入保存，**不进版本库**。

### 测试

```bash
# 单元测试（领域逻辑：状态机、冲突检测、落点计算、DAO）
./gradlew :app:testDebugUnitTest

# 仪器测试（Compose UI：拖拽、排期、图标状态）
./gradlew :app:connectedDebugAndroidTest
```

---

## 文档

| 文档 | 内容 |
|------|------|
| [`CONTEXT.md`](CONTEXT.md) | 产品词汇表、领域规则、交互规范、设计约束 |
| [`docs/adr/0001-ai-via-backend-proxy.md`](docs/adr/0001-ai-via-backend-proxy.md) | AI 走后端代理的架构决策记录 |
| [`CLAUDE.md`](CLAUDE.md) | 项目工作规范（Agent 协作、Issue 追踪、Triage 标签） |

### 关键领域规则（Cascade）

| 场景 | 行为 |
|------|------|
| 编辑已排项的标题/时长 | 日程格子自动跟着变（同一实体引用） |
| 删除已排项 | 对应日程格子自动清空 |
| 任务放弃 | 所有子项目自动放弃 + 日程全部清空 |
| 同任务内时间重叠 | **拒绝放置**（冲突） |
| 跨任务时间重叠 | **允许**，用任务色彩区分 |

---

## 下载

前往 [Releases](https://github.com/Nature-ce/zhixing/releases) 页下载最新 APK。当前最新版本：

- **v0.3.0** — 拖拽排期精度提升到 5 分钟 + 落点预览幽灵块（冲突变红）+ 修复 15 分钟块再次拖动变 30 分钟的 bug

> ⚠️ Release APK 为未签名包，仅可直接安装测试。发布到应用市场需自行签名。

---

## 许可

本项目为个人作品，暂未选择开源协议。如需使用请联系作者。
