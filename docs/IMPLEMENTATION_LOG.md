# LittleClicker 实现记录

最后更新：2026-03-24

## 2026-03-22
- 初始化 Compose + Navigation 架构。
- 完成 `首页 / 脚本管理 / 关于` 三 Tab。
- 完成首页权限引导（悬浮窗、无障碍、电池优化）与状态检测。
- 增加无障碍服务骨架及配置文件。
- 新增 `docs/MODULES.md` 模块说明文档。
- 根据文档规范调整：`AI_MEMORY.md` 仅保留用户要求。
- 修复运行时崩溃：补充 Compose 图标依赖 `androidx.compose.material:material-icons-extended`，解决 `NoClassDefFoundError: androidx.compose.material.icons.Icons`。
- UI 体系迁移：MainActivity 从 Material3 组件切换到 miuix 组件（`MiuixTheme`、`Scaffold`、`NavigationBar`、`NavigationBarItem`、`Card`、`Button`、`Text`），保留原有业务逻辑与导航结构。
- 修复 About 页面崩溃：将 `painterResource(R.mipmap.ic_launcher)` 替换为 `R.drawable.ic_launcher_foreground`，避免 Compose 加载 Adaptive Icon 导致 `IllegalArgumentException`。
- 实现 `P2` 悬浮窗服务：新增 `FloatingWindowService`（`LifecycleService`），使用 `WindowManager + ComposeView` 渲染可拖拽控制面板。
- 悬浮面板新增 4 个入口按钮：`录制`、`播放/暂停`、`设置`、`关闭`，均已接入占位行为。
- 新增多靶标可视化与拖拽定位：默认添加 2 个靶标，支持继续添加、拖拽和删除；靶标序号按添加顺序稳定展示，删除后自动重排避免重复序号。
- 首页“开启悬浮窗”按钮接入服务启动逻辑，并保留权限前置校验。
- Manifest 新增 `FloatingWindowService` 声明；Gradle 新增 `androidx.lifecycle:lifecycle-service` 依赖。
- 修复悬浮窗崩溃：为 `Service` 中 `ComposeView` 显式绑定 `setViewTreeLifecycleOwner(this@FloatingWindowService)`，解决 `ViewTreeLifecycleOwner not found` 异常。
- 修复悬浮窗崩溃（Compose 新约束）：为 `ComposeView` 绑定 `setViewTreeSavedStateRegistryOwner(...)`，并在服务内补充 `SavedStateRegistryOwner`，解决 `doesn't propagateViewTreeSavedStateRegistryOwner` 异常。
- 修复服务创建崩溃：`SavedStateRegistryOwner` 初始化从构造阶段后移到 `FloatingWindowService.onCreate()`，解决 `Unable to create service` / `Lifecycle.getCurrentState()` 空指针。
- 修复服务创建期空指针残留：`OverlaySavedStateOwner` 改为内部维护独立 `LifecycleRegistry`（置为 `CREATED` 后再 `performAttach/performRestore`），避免依赖 `LifecycleService.lifecycle` 的初始化时序；服务销毁时同步置 `DESTROYED`。
- 修复 `Restarter must be created only during owner's initialization stage`：调整 `OverlaySavedStateOwner` 初始化顺序，先 `performAttach/performRestore`，再将 `LifecycleRegistry` 置为 `CREATED`，满足 SavedState 初始化阶段约束。
- 按测试流程执行：编译（`assembleDebug`）-> 安装（`adb install -r`）-> 清空并抓取 logcat -> 启动 App；当前 logcat 未再出现 `FloatingWindowService` 的 FATAL EXCEPTION。
- 编译验证：`./gradlew :app:assembleDebug` 通过。

## 2026-03-23
- 继续落实 `P2` 悬浮窗 UI：控制面板改为浅色半透明背景，提升可读性。
- 将悬浮面板 4 个功能入口由文字按钮改为竖向图标按钮（录制、播放/暂停、设置、关闭），保留原有占位行为与拖拽能力。
- 编译验证：`./gradlew :app:assembleDebug` 通过。

## 2026-03-23（自动点击优先重构）
- 导航与信息架构调整：默认页从“首页权限向导”切换为“自动点击”，保留三 Tab（自动点击/脚本管理/关于）。
- 自动点击数据层落地：新增 `AutoClickPoint`、`AutoClickProfile`、`ScriptDraft`、`AutoClickRunState` 等模型与执行步骤展开逻辑。
- 本地保存能力落地（私有 JSON）：
  - 自动点击配置保存/读取：`filesDir/autoclick/profile.json`。
  - 脚本草稿保存/读取：`filesDir/scripts/*.json`。
- 新增 `AutoClickCoordinator` 统一状态与操作入口：页面、悬浮窗、无障碍服务共享 `StateFlow`。
- 自动点击页面实现：
  - 保留三项权限引导卡片；
  - 支持点位参数配置（延迟/触摸时长/重复次数）；
  - 支持全局循环、定时开始（应用内可靠）、立即开始、暂停/继续、停止、保存。
- 无障碍服务实现自动点击执行引擎：基于 `dispatchGesture` 串行执行点击步骤，支持并发保护、暂停/继续、停止与状态回传。
- 悬浮窗改为自动点击专用：
  - 继续支持面板拖动和点位拖动；
  - 面板功能改为“添加点位/开始暂停/保存/关闭”，移除录制语义。
- 脚本管理页改为“脚本草稿管理”最小版：支持新建保存、覆盖保存、列表展示与加载。
- 新增单元测试：
  - 自动点击配置 JSON 序列化/反序列化；
  - 脚本草稿空动作序列化/反序列化；
  - 执行步骤展开规则（每点重复 + 全局循环）。
- 验证结果：
  - `./gradlew :app:testDebugUnitTest` 通过。
  - `./gradlew :app:assembleDebug` 通过。

## 2026-03-23（悬浮窗拖动与长按编辑增强）
- 悬浮窗拖动增强：面板拖动支持自由移动，并加入屏幕边界约束，防止拖出可视区域。
- 点击点拖动增强：点击点拖动定位时同步加入边界约束，防止拖出屏幕外。
- 点击点编辑方式调整：
  - 自动点击页点位列表改为只读展示；
  - 编辑入口改为“长按悬浮窗中的对应点击点”；
  - 长按后弹出编辑窗口，可调整 `X/Y`、`delayMs`、`touchDurationMs`、`repeatCount` 并保存。
- 协调器能力补充：新增点击点绝对坐标设置接口，支撑边界约束拖动与弹窗保存。
- 验证结果：
  - `./gradlew :app:testDebugUnitTest` 通过。
  - `./gradlew :app:assembleDebug` 通过。

## 2026-03-23（悬浮窗卡死修复）
- 修复“开启悬浮窗后主进程卡死”问题：将悬浮窗从单个全屏 Overlay 改为多窗口 Overlay（面板窗口 + 点击点独立窗口），避免整屏窗口拦截主界面触摸。
- 保留并增强交互：
  - 面板自由拖动并限制在屏幕内；
  - 点击点自由拖动并限制在屏幕内；
  - 长按点击点弹出编辑窗口（坐标、延迟、触摸时长、重复次数）。
- 验证结果：
  - `./gradlew :app:testDebugUnitTest` 通过。
  - `./gradlew :app:assembleDebug` 通过。

## 2026-03-23（悬浮窗动作侧栏与录制增强）
- 悬浮窗侧边栏按钮顺序调整为：`运行`、`录制/停止录制`、`添加动作`、`删除最新动作`、`保存`、`关闭`。
- 新增录制模式：
  - 点击“录制”后进入触摸采集模式，再次点击按钮停止录制；
  - 录制点击会直接写入动作列表，并按录制顺序计数。
- 动作模型扩展为 `点击/滑动`：
  - `AutoClickPoint` 新增动作类型与滑动终点字段；
  - 无障碍执行引擎新增滑动派发能力（`dispatchGesture` 轨迹滑动）。
- 兼容性修复：历史配置 JSON 无 `actionType` 字段时，读取默认回填为 `Click`，避免悬浮窗展示阶段空值崩溃。
- 悬浮窗任务列表实时更新：列表改为在悬浮窗内部直接订阅 `AutoClickCoordinator.profile`，新增/录制/编辑/删除后即时刷新。
- 运行与录制按钮优化：
  - 运行按钮在进入运行态后显示停止图标；
  - 录制按钮改为中文字按钮（`录` / `停`）。
- 录制能力增强：录制层支持区分点击与滑动（基于触摸位移阈值），并写入对应动作类型。
- 悬浮窗面板新增动作列表展示，格式与画圈标签统一为：`1.点击`、`2.滑动`...
- 动作列表支持行内操作：
  - 编辑（铅笔图标）：可修改动作类型、起点/终点、延迟、触摸时长、重复次数；
  - 删除（垃圾桶图标）：删除指定动作。
- 画圈点位标签与面板保持一致，显示 `序号.动作类型`。
- 回归验证：
  - `./gradlew :app:assembleDebug` 通过；
  - `./gradlew :app:testDebugUnitTest` 通过；
  - `adb install -r` 安装成功；
  - 清空 logcat 后启动 App，未发现 `FATAL EXCEPTION`/`AndroidRuntime` 崩溃记录。

## 2026-03-23（悬浮窗状态刷新修复）
- 修复悬浮窗面板状态不刷新问题：
  - 删除/新增动作后，侧栏动作列表不更新；
  - 点击“运行/录制”后，按钮图标不切换到停止态。
- 根因修复：
  - `ComposeView` 的 `ViewTreeLifecycleOwner` 从 `FloatingWindowService` 切换为内部 `OverlaySavedStateOwner`；
  - 将 `OverlaySavedStateOwner` 生命周期状态从 `CREATED` 提升为 `RESUMED`，保证悬浮窗 Compose 视图持续可重组。
- 验证结果：
  - `./gradlew :app:assembleDebug` 通过。

## 2026-03-23（移除脚本管理功能）
- 导航精简：`MainActivity` 底部导航移除“脚本管理”Tab，保留“自动点击/关于”两页。
- 页面清理：删除 `ScriptManageScreen.kt`，录制能力统一收敛到自动点击悬浮窗侧栏。
- 数据层清理：
  - 删除 `ScriptDraft` 模型；
  - `AutoClickCoordinator` 删除草稿相关状态与操作接口；
  - `AutoClickRepository` 删除草稿读写与 `filesDir/scripts` 存储逻辑。
- 文案调整：自动点击首页提示更新为“录制与运行控制已整合到自动点击悬浮窗”。
- 单测清理：移除脚本草稿序列化测试，仅保留自动点击配置与执行步骤测试。
- 验证结果：
  - `./gradlew :app:assembleDebug` 通过；
  - `./gradlew :app:testDebugUnitTest` 通过。

## 2026-03-23（AutoClick 提示词库随机展示）
- 自动点击页提示文案改为词库随机展示：
  - 新增资源文件：`app/src/main/res/raw/autoclick_tips.txt`（每行一条提示）。
  - `AutoClickScreen` 中新增词库读取与随机选择逻辑，权限未齐全时展示 `提示：{随机文案}`。
- 稳定性兜底：
  - 读取失败、资源为空或全空行时，自动回退到默认文案，避免页面空提示或崩溃。

## 2026-03-23（悬浮窗动作列表滚动）
- 悬浮窗面板“动作列表”由静态纵向直排改为可滚动列表。
- 列表容器增加高度上限（`heightIn(max = 220.dp)`）：动作较多时在面板内部滚动，不再继续向下延伸撑高悬浮窗。
- 验证结果：
  - `./gradlew assembleDebug` 通过。

## 2026-03-23（手动保存策略 + 配置复制）
- 调整保存策略：用户编辑动作后不再自动落盘，改为仅更新当前内存状态，需手动点击“保存”才写入配置文件。
  - 移除点击点拖动结束时的自动 `saveProfile()`；
  - 移除点击点编辑弹窗“保存”后的自动 `saveProfile()`。
- 配置管理新增“复制”功能：
  - 配置列表每项新增“复制”按钮；
  - 点击后创建同内容新配置，命名为 `原配置名_副本`，并保留当前激活配置不变。
- 协调器新增复制接口：`AutoClickCoordinator.duplicateProfile(profileId)`。
- 验证结果：
  - `./gradlew assembleDebug` 通过。
  - `./gradlew :app:testDebugUnitTest` 通过。
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk` 安装成功。
  - 清空 logcat 后启动 App（`adb shell monkey -p com.example.littleclicker -c android.intent.category.LAUNCHER 1`），未检出 `FATAL EXCEPTION` / `Process: com.example.littleclicker` 崩溃日志。

## 2026-03-23（未保存改动回滚修复）
- 修复问题：删除/编辑动作后仅关闭悬浮窗，重新打开仍沿用内存改动。
- 修复方案：
  - `AutoClickCoordinator` 新增 `discardUnsavedChanges()`，从当前激活配置文件重载数据；
  - 悬浮窗关闭按钮路径与 `ACTION_STOP` 路径统一调用回滚，确保关闭后恢复到已保存状态。
- 验证结果：
  - `./gradlew assembleDebug` 通过。
  - `./gradlew :app:testDebugUnitTest` 通过。
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk` 安装成功。
  - 清空 logcat 后启动 App，未检出 `FATAL EXCEPTION` / `Process: com.example.littleclicker` 崩溃日志。

## 2026-03-24（首次点击延迟修复）
- 修复问题：动作 `delayMs` 原先在动作派发后执行，导致第一下点击总是立即触发。
- 修复方案：`AutoClickAccessibilityService.executePointSequence` 调整为“先等待当前动作延迟，再派发手势”，使首次点击也遵守配置延迟。
- 验证结果：
  - `./gradlew :app:testDebugUnitTest` 通过。
  - `./gradlew :app:assembleDebug` 通过。

## 2026-03-24（启动固定等待 100ms）
- 需求调整：点击“开始运行”后，先固定等待 `100ms`，再执行动作队列。
- 实现方案：`AutoClickAccessibilityService.executeProfile` 在进入循环前新增统一启动延迟（`START_TRIGGER_DELAY_MS = 100L`），并保留取消/暂停检查。
- 验证结果：
  - `./gradlew :app:testDebugUnitTest` 通过。
  - `./gradlew :app:assembleDebug` 通过。

## 2026-03-24（定时触发 + NTP 校时 + 独立定时悬浮窗）
- 数据层扩展：
  - `AutoClickProfile` 新增 `ntpServerHost`（默认 `ntp.aliyun.com`）与 `scheduleRuleHms`；
  - 新增 `TimeSyncState`，用于承载校时状态、偏移与延迟；
  - `AutoClickRepository` 兼容旧 JSON：缺失新字段时自动回填默认值。
- 新增 NTP 能力：
  - 新增 `SntpClient`（UDP 123）并返回 `offsetMillis` / `delayMillis`；
  - `AutoClickCoordinator` 新增 `syncNtpTime`、`updateNtpServer`、`currentAlignedNowMillis`；
  - 校时失败自动回退本机时间，且不阻塞定时配置。
- 定时规则升级：
  - 新增 `scheduleAtHms(hour, minute, second)`，仅保留一条规则并覆盖旧规则；
  - 若设定 `hh:mm:ss` 已过期，直接提示失败且不生效；
  - 触发逻辑改为短轮询对齐时钟（`30ms`），到点自动 `startNow(fromSchedule = true)`。
- UI 重构（AutoClickScreen）：
  - 定时卡片改为大字时钟样式，实时展示 `HH:mm:ss.S`（1 位小数）；
  - 增加 `选择时间`（自定义 `hh:mm:ss` 选择 GUI）、`开启/关闭定时悬浮窗`、`配置ntp服务器` 三入口；
  - 新增 NTP 状态文案、设定时间文案、运行状态文案；
  - 原“动作悬浮窗”文案改名，避免与定时悬浮窗混淆。
- 新增服务：
  - 新增 `TimerFloatingWindowService`（独立服务，不替换 `FloatingWindowService`）；
  - 灰色半透明背景、可拖动、单行展示：当前时间 + 设定时间 + 运行状态。
- 系统清单：
  - `AndroidManifest.xml` 新增 `INTERNET` 权限；
  - 注册 `TimerFloatingWindowService`。
- 测试补充：
  - 扩展 `AutoClickSerializationTest`：新增字段 round-trip、旧 JSON 默认回填、`scheduleAtHms` 过期/有效分支；
  - 新增 `UiHelpersFormatTest`：覆盖 `formatHms` 与 `formatHmsWithTenths`。
- 联调流程（按项目约定）：
  - 临时在 `MainActivity` 注入“启动自动触发新定时能力”代码，并提示无障碍状态；
  - 执行：编译 -> 安装 -> 清空并抓取 logcat -> 启动 App；
  - 观察到无 `FATAL EXCEPTION` / `Process: com.example.littleclicker` 崩溃后，已移除该临时代码。
- 验证结果：
  - `./gradlew :app:testDebugUnitTest` 通过。
  - `./gradlew :app:assembleDebug` 通过。
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk` 安装成功。
  - 清空 logcat 后启动 App，未检出 LittleClicker 崩溃日志。
