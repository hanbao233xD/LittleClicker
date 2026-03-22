# 安卓自动点击器开发计划书（AI 可执行规范版）

最后更新：2026-03-22  
适用项目：LittleClicker（Kotlin + Jetpack Compose）

## A. 当前基线（Current Baseline）
1. 已完成阶段：`P1`（项目初始化与底部导航架构）。
2. 默认起始阶段：`P2`（除非你明确要求重走 `P1`）。
3. 基线依据：`docs/IMPLEMENTATION_LOG.md` 中 `2026-03-22` 记录。
4. 基线约束：后续实现不得破坏 `首页/脚本管理/关于` 三 Tab 和已有权限引导流程。

## B. 全局执行协议（Global Protocol）
1. 单阶段推进：每次只实现一个 `Phase ID`，禁止跨阶段抢跑。
2. 先实现再验证：代码完成后必须执行编译检查，失败先修复再汇报。
3. 报错即回传：若无法在当前轮修复，按 `D. 统一失败回传模板` 返回完整信息。
4. 最小变更原则：仅做该阶段目标和“最小必要依赖”改动。
5. 文档同步：每次关键改动后，至少更新 `docs/IMPLEMENTATION_LOG.md`；如涉及模块职责变化，同步更新 `docs/MODULES.md`。
6. 非目标不改：不做样式大改、不做架构重写、不做无关重构。

## C. 统一输出契约（Output Contract）
每次阶段开发完成后的回复必须严格包含以下 5 段，且按顺序输出：
1. 变更摘要：本轮完成了什么，未完成什么。
2. 文件清单：新增/修改文件路径和用途。
3. 编译结果：执行命令、结果（通过/失败）、关键日志摘要。
4. 风险/待确认：仍存在的风险点、需要用户确认的决策。
5. docs 更新项：本轮同步了哪些 `docs/*` 文件以及更新内容。

## D. 统一失败回传模板（Failure Template）
当阶段无法完成时，按以下模板回传，不得省略：

```text
[Phase]
P{N} - {Phase Name}

[What I Tried]
- 列出已尝试的实现步骤（按时间顺序）

[Repro Steps]
1. ...
2. ...
3. ...

[Command]
`./gradlew :app:assembleDebug` 或本轮实际命令

[Full Error Log]
<粘贴完整错误日志，不截断>

[Recent Changed Files]
- path/to/file1
- path/to/file2

[Blocking Analysis]
- 我判断的根因
- 下一步最小修复方案
```

## E. 阶段规范（Phase Specs 1-7）

### P1 / 项目初始化与底部导航架构（已完成，仅回归）
**Objective（目标）**
- 保持可运行的应用基座：Compose + 导航 + 权限引导 + 关于页基础信息。

**In Scope（本阶段必须实现）**
- 回归验证现有 `首页/脚本管理/关于` 三 Tab 正常切换。
- 回归验证悬浮窗、无障碍、电池优化引导入口可打开系统设置。
- 回归验证关于页基础展示（图标/名称/版本/列表项）正常。

**Out of Scope（明确禁止）**
- 不新增悬浮窗服务逻辑。
- 不新增脚本存储和执行逻辑。
- 不新增引擎能力（click/swipe）。

**Implementation Notes（实现约束）**
- 最小必要依赖：仅允许修复 `P1` 回归失败所需依赖。
- 保持现有 miuix 风格和导航结构不变。

**Acceptance Criteria（可验证验收项）**
1. 三个 Tab 均可进入且无崩溃。
2. 三项授权卡片点击后可跳转对应设置页。
3. “开启悬浮窗”按钮仅在全部授权时可用。
4. `:app:assembleDebug` 编译通过。

**Deliverables（代码与文档产物）**
- 仅回归修复相关代码（如有）。
- `docs/IMPLEMENTATION_LOG.md` 记录回归结果。

**Runbook（执行命令与失败回传）**
- 执行：`./gradlew :app:assembleDebug`
- 失败：严格使用 `D` 模板回传。

### P2 / 可视化悬浮窗与打靶系统
**Objective（目标）**
- 提供用户可见、可操作的悬浮控制面板与多靶标拖拽定位能力。

**In Scope（本阶段必须实现）**
- 新建 `FloatingWindowService`（`LifecycleService`）。
- 使用 `WindowManager + ComposeView` 渲染悬浮面板。
- 面板提供 4 个可点击入口：录制、播放/暂停、设置、关闭。
- 支持添加多个靶标并拖动位置，显示递增序号（1,2,3...）。

**Out of Scope（明确禁止）**
- 不实现真实录制和动作落盘（属于 `P4`）。
- 不实现 click/swipe 引擎（属于 `P3`）。
- 不实现脚本管理列表（属于 `P5`）。

**Implementation Notes（实现约束）**
- 最小必要依赖：仅新增悬浮窗服务所需权限/声明和 UI 状态管理。
- 靶标顺序必须稳定（按添加顺序编号）。

**Acceptance Criteria（可验证验收项）**
1. 悬浮面板可显示、可拖动、可关闭。
2. 四个按钮均可触发对应占位行为或回调。
3. 至少可添加 2 个靶标，拖动后位置更新可见。
4. 靶标编号与添加顺序一致，删除后不出现重复编号冲突。
5. `:app:assembleDebug` 编译通过。

**Deliverables（代码与文档产物）**
- 悬浮窗服务与面板相关代码。
- 如有新增组件，更新 Manifest 声明。
- 更新 `docs/IMPLEMENTATION_LOG.md`，必要时更新 `docs/MODULES.md`。

**Runbook（执行命令与失败回传）**
- 执行：`./gradlew :app:assembleDebug`
- 失败：严格使用 `D` 模板回传。

### P3 / 核心引擎与安全机制
**Objective（目标）**
- 具备基础点击/滑动执行能力与音量减紧急停止机制。

**In Scope（本阶段必须实现）**
- 在无障碍服务中实现 `click(x, y)`。
- 在无障碍服务中实现 `swipe(startX, startY, endX, endY, duration)`。
- 监听音量减按键并触发紧急停止。
- 紧急停止后清空待执行任务并提示“已紧急停止”。

**Out of Scope（明确禁止）**
- 不做录制保存（`P4`）。
- 不做脚本列表编辑（`P5`）。
- 不做随机扰动与内存优化（`P6`）。

**Implementation Notes（实现约束）**
- 最小必要依赖：仅允许引入执行手势和任务中断所需改动。
- 所有执行入口都必须可被“紧急停止”统一中断。

**Acceptance Criteria（可验证验收项）**
1. `click` 能通过 `GestureDescription` 发起单点点击。
2. `swipe` 能通过 `GestureDescription` 发起滑动。
3. 音量减按下后，执行队列清空且后续动作不再发出。
4. 触发紧急停止后 Toast 文案正确且仅提示一次。
5. `:app:assembleDebug` 编译通过。

**Deliverables（代码与文档产物）**
- 无障碍服务引擎增强代码。
- 更新 `docs/IMPLEMENTATION_LOG.md`。

**Runbook（执行命令与失败回传）**
- 执行：`./gradlew :app:assembleDebug`
- 失败：严格使用 `D` 模板回传。

### P4 / 傻瓜式录制与文件化保存
**Objective（目标）**
- 将录制触摸动作转换为可编辑 JSON，并保存到 Documents。

**In Scope（本阶段必须实现）**
- 录制模式下拦截触摸并采集坐标、延迟。
- 定义 `Action`、`Script` 数据模型。
- 录制结束弹窗输入脚本名。
- 使用 `Gson` 序列化并保存 `Documents/*.json`。

**Out of Scope（明确禁止）**
- 不做脚本管理页编辑器（`P5`）。
- 不做 NTP 定时（`P5`）。
- 不做分享导入（`P7`）。

**Implementation Notes（实现约束）**
- 最小必要依赖：仅允许录制层、模型层、文件管理层改动。
- JSON 结构需保证“手动编辑后可再次读取”。

**Acceptance Criteria（可验证验收项）**
1. 开启录制后可记录至少 3 次触摸动作。
2. 录制结束必须要求输入脚本名称再保存。
3. 生成 JSON 文件出现在 Documents 目录，文件可见。
4. 读取该 JSON 并反序列化不报错。
5. `:app:assembleDebug` 编译通过。

**Deliverables（代码与文档产物）**
- `Action/Script` 模型与文件管理工具。
- 录制入口与结束交互代码。
- 更新 `docs/IMPLEMENTATION_LOG.md`，必要时更新 `docs/MODULES.md`。

**Runbook（执行命令与失败回传）**
- 执行：`./gradlew :app:assembleDebug`
- 失败：严格使用 `D` 模板回传。

### P5 / 脚本管理页与精准定时
**Objective（目标）**
- 建立脚本列表、编辑、任务配置与定时执行基础能力。

**In Scope（本阶段必须实现）**
- 列表展示 Documents 下所有脚本及动作数量。
- 提供动作坐标/延迟编辑并覆盖保存。
- 列表项点击弹出 BottomSheet 配置循环次数与点击间隔。
- 关于页支持 NTP 服务器配置与基础校时请求。
- 悬浮窗支持定时倒计时并在目标时刻执行脚本。

**Out of Scope（明确禁止）**
- 不做随机扰动（`P6`）。
- 不做分享导入、长按、关联应用（`P7`）。

**Implementation Notes（实现约束）**
- 最小必要依赖：只允许脚本管理 UI、存储读写、定时调度相关改动。
- 高级项失败降级策略：NTP 请求失败时自动回退本机时间，并提示“已使用本机时间”，不阻塞脚本保存和本地执行。

**Acceptance Criteria（可验证验收项）**
1. 脚本列表可正确显示文件名与动作数量。
2. 修改动作参数后可覆盖保存并重新加载生效。
3. BottomSheet 配置项提交后可传递到执行参数。
4. NTP 配置可保存，校时失败时触发降级提示并继续可用。
5. `:app:assembleDebug` 编译通过。

**Deliverables（代码与文档产物）**
- 脚本管理页列表与编辑子页面代码。
- 任务配置 BottomSheet 与定时基础能力代码。
- 更新 `docs/IMPLEMENTATION_LOG.md` 和 `docs/MODULES.md`。

**Runbook（执行命令与失败回传）**
- 执行：`./gradlew :app:assembleDebug`
- 失败：严格使用 `D` 模板回传。

### P6 / 防检测机制与内存优化
**Objective（目标）**
- 提升执行拟真性，降低崩溃与内存泄漏风险。

**In Scope（本阶段必须实现）**
- 点击坐标加入 `-4..+4 px` 随机偏移。
- 动作延迟加入 `-15..+15 ms` 随机波动。
- 悬浮窗 UI 渲染增加防崩保护。
- `onDestroy` 中释放 `ComposeView` 与窗口资源。

**Out of Scope（明确禁止）**
- 不新增脚本功能形态。
- 不新增分享导入或关联应用能力（`P7`）。

**Implementation Notes（实现约束）**
- 最小必要依赖：仅执行引擎与服务生命周期相关改动。
- 随机扰动必须可开关，默认开启。

**Acceptance Criteria（可验证验收项）**
1. 同一目标点多次执行存在可观测坐标差异。
2. 延迟值实际落点包含随机波动且不为负数。
3. 服务销毁后无残留悬浮窗视图。
4. 长时间运行后手动停用服务不出现明显泄漏迹象。
5. `:app:assembleDebug` 编译通过。

**Deliverables（代码与文档产物）**
- 引擎扰动策略与开关配置代码。
- 服务销毁资源释放代码。
- 更新 `docs/IMPLEMENTATION_LOG.md`。

**Runbook（执行命令与失败回传）**
- 执行：`./gradlew :app:assembleDebug`
- 失败：严格使用 `D` 模板回传。

### P7 / 高阶生态功能
**Objective（目标）**
- 增加脚本分享导入、长按动作、关联应用自动唤醒能力。

**In Scope（本阶段必须实现）**
- 脚本分享：`Intent.ACTION_SEND` 分享 JSON。
- 脚本导入：系统文件选择器导入 JSON 并入库。
- `Action` 扩展 `LONG_PRESS`，执行引擎支持长按。
- 配置关联应用后，前台应用命中时自动唤醒悬浮窗并准备脚本。

**Out of Scope（明确禁止）**
- 不改写前面阶段已通过的核心流程。
- 不新增无关社交系统或云同步。

**Implementation Notes（实现约束）**
- 最小必要依赖：仅分享导入、动作类型扩展、前台应用监听相关改动。
- 高级项失败降级策略：
  - 导入失败：提示错误并保留现有脚本列表，不清空数据。
  - 关联应用监听失败：保持手动唤起悬浮窗路径可用。

**Acceptance Criteria（可验证验收项）**
1. 脚本可通过系统分享面板发送给其他应用。
2. 从文件管理器选择 JSON 后可导入并显示在列表中。
3. `LONG_PRESS` 可被保存、读取并执行。
4. 命中关联应用时可自动唤醒悬浮窗；失败时仍可手动加载脚本。
5. `:app:assembleDebug` 编译通过。

**Deliverables（代码与文档产物）**
- 分享、导入、长按、关联唤醒功能代码。
- 更新 `docs/IMPLEMENTATION_LOG.md` 和 `docs/MODULES.md`。

**Runbook（执行命令与失败回传）**
- 执行：`./gradlew :app:assembleDebug`
- 失败：严格使用 `D` 模板回传。

## F. 可复制口令（Quick Commands）

### 1) 总控口令 v2（推荐）
```text
你现在按 docs/AUTOMATION_CLICKER_PLAN.md 的 AI 可执行规范版开发：
1) 严格只做我指定的 Phase ID，不跨阶段抢跑。
2) 完成后必须先编译验证，再按 Output Contract 的 5 段格式回复。
3) 若失败，按 Failure Template 回传完整日志、复现步骤和最近改动文件。
4) 每次关键改动后同步更新 docs 文档。
当前要执行的 Phase ID：<例如 P2>
```

### 2) 阶段开工口令（按 Phase ID）
```text
开始执行 <P2~P7 之一>。按 docs/AUTOMATION_CLICKER_PLAN.md 的该阶段规范实现，仅做 In Scope，严格排除 Out of Scope。完成后先编译，再按 5 段输出契约汇报。
```

### 3) 回归验证口令（冒烟复查）
```text
请仅做回归验证，不新增功能：按 docs/AUTOMATION_CLICKER_PLAN.md 检查当前阶段及上一个阶段的 Acceptance Criteria，并汇报通过/失败项与最小修复建议。
```

## G. 术语与边界（Glossary & Boundaries）
1. 必要依赖改动：为满足当前阶段 In Scope 不可避免的依赖/权限/声明修改。
2. 抢跑：实现了当前阶段 Out of Scope 中定义的功能，或提前接入后续阶段主能力。
3. 不算抢跑的改动：
   - 修复当前阶段实现导致的编译错误。
   - 为当前阶段能力补齐最小权限或清单声明。
   - 为当前阶段验收补充最小数据结构字段（不引入后续逻辑）。
4. 关键改动：新增服务、核心模型变更、执行引擎变更、持久化格式变化、导航结构变化。
5. 完成定义（Definition of Done）：
   - 当前阶段 Acceptance Criteria 全部通过。
   - 有编译验证结果。
   - 有 docs 同步记录。

## 使用备注
1. 本计划用于多轮协作，不要求 AI 一次性完成全部阶段。
2. 默认从 `P2` 开始推进；`P1` 仅做回归清单。
3. 如你想重走 `P1`，直接在口令里指定 `Phase ID: P1`。
