# LittleClicker 实现记录

最后更新：2026-04-05

## 2026-04-05（自动点击支持三大金刚键：Home/Back/多任务）
- 需求实现：
  - 自动点击动作新增 `Home`、`Back`、`多任务` 三种系统按键模拟能力。
- 代码改动：
  - `AutoClickModels`：扩展 `AutoClickActionType`，新增展示名与动作能力标记（是否需要坐标/触摸时长）。
  - `AutoClickAccessibilityService`：新增全局动作分发，按动作类型调用 `performGlobalAction(GLOBAL_ACTION_HOME/BACK/RECENTS)`。
  - `FloatingWindowService`：添加动作弹窗新增三种动作；非坐标动作不再显示屏幕点位气泡；编辑弹窗按动作类型动态显示字段。
  - `AutoClickCoordinator` / `AutoClickRepository`：补齐新动作类型的创建、归一化与 JSON 反序列化兼容。
  - `AutoClickScreen`：主页面“添加动作”支持新增三种动作；动作列表与编辑弹窗适配非坐标动作文案与字段展示。
- 验证结果：
  - `./gradlew :app:assembleDebug` 通过。

## 2026-04-05（编辑点击点弹窗支持滚动）
- 问题现象：
  - 编辑点击点时，弹窗内容在小屏设备上超出可视区域，底部配置项无法触达。
- 修复方案：
  - `AutoClickScreen.showPointEditDialog`：将编辑表单由直接 `setView(LinearLayout)` 改为 `setView(ScrollView)`，表单内容作为 `ScrollView` 子视图。
  - `FloatingWindowService.showPointEditDialog`：同步采用 `ScrollView` 包裹编辑表单，保持主界面与悬浮窗编辑体验一致。
- 影响范围：
  - 支持在编辑弹窗内上下滚动，底部字段（如触摸时长、重复次数）可正常编辑。
- 验证结果：
  - `./gradlew :app:compileDebugKotlin` 通过。

## 2026-04-05（运行中底部红字提示 + 音量下停止引导）
- 需求实现：
  - 自动点击运行期间，在屏幕底部持续显示透明红色提示文案：`提示：按音量下键可强制停止自动点击`。
  - 提示层不依赖动作悬浮窗开关；即使悬浮窗关闭，只要自动点击在执行中仍会显示。
- 代码改动（`AutoClickAccessibilityService`）：
  - 新增 `TYPE_ACCESSIBILITY_OVERLAY` 文本提示层，位置固定在屏幕底部居中。
  - 提示层窗口参数增加 `FLAG_NOT_TOUCHABLE` / `FLAG_NOT_FOCUSABLE`，确保不拦截触摸、不影响正常点击。
  - 在自动点击任务 `start/stop/异常/销毁` 生命周期中统一管理提示层显示与移除。
  - 增加执行 `token` 校验，避免“旧任务 finally”误删新任务提示层的并发问题。
- 资源改动：
  - `app/src/main/res/values/strings.xml` 新增 `autoclick_force_stop_hint` 文案资源。

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

## 2026-03-24（主悬浮窗模式选择 + 最小化切换）
- 主悬浮窗交互调整：
  - 将侧栏“关闭”键改为“最小化切换”行为（不再直接关闭服务）；
  - 点击一次进入最小化态，点击“最小化”再次恢复完整态；
  - 最小化态仅保留竖排两个按钮：`运行`、`最小化`。
- 状态提示：
  - 每次点击最小化切换都会 Toast 提示当前状态（已最小化 / 已恢复完整模式）。
- 首页新增模式选择：
  - 在“动作悬浮窗与运行方式”卡片中新增 `悬浮窗模式` 下拉；
  - 选项为：`编辑模式`、`运行模式`；
  - 映射规则：编辑模式=完整最大化面板，运行模式=最小化面板（作为悬浮窗启动默认形态）。
- 验证结果：
  - `./gradlew :app:assembleDebug` 通过。
  - `./gradlew :app:testDebugUnitTest` 通过。
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk` 安装成功。

## 2026-03-24（悬浮窗启动权限闸门）
- 启动闸门统一：动作悬浮窗与定时悬浮窗在“启动前”统一执行全量权限检查。
- 检查项：`悬浮窗权限`、`无障碍服务`、`忽略电池优化` 三项必须全部满足。
- 引导策略：任一权限缺失时，立即跳转到对应系统设置页并 Toast 提示，且本次不启动悬浮窗。
- 关闭策略：已开启悬浮窗的关闭操作不受权限闸门影响，仍可直接关闭。
- 实现位置：
  - `UiHelpers.ensureOverlayStartPermissions(context)` 新增统一检查与引导逻辑；
  - `AutoClickScreen` 的动作悬浮窗开关与定时悬浮窗开关均接入该逻辑。

## 2026-03-24（深色模式适配）
- 自动点击页（`AutoClickScreen`）适配深色模式：
  - 页面背景渐变改为深浅模式动态配色；
  - 卡片容器统一改为主题色 `surfaceContainer`，不再硬编码白底；
  - 成功态/强调文案颜色改为深浅模式可读配色，解决暗色下浅底白字对比不足问题；
  - 定时大时钟容器在暗色模式切换为深色底，保持文字可读性。
- 配置管理页（`ConfigManageScreen`）同步适配：
  - 页面背景、卡片底色、激活配置高亮与“当前使用中”状态色均支持深浅模式。
- 定时悬浮窗（`TimerFloatingWindowService`）主题跟随系统深浅模式，保留灰色半透明风格并调整边框对比度。
- 验证结果：
  - `./gradlew :app:assembleDebug` 通过。
  - `./gradlew :app:testDebugUnitTest` 通过。

## 2026-03-24（配置管理页 TopAppBar + 深色适配完善）
- `ConfigManageScreen` 顶部结构改为 `miuix Scaffold + TopAppBar`，移除原“标题 + 返回按钮”行。
- 返回交互迁移到 TopAppBar 导航图标（左上角返回箭头）。
- 页面内容区继续保留深浅模式动态配色：
  - 背景渐变、卡片底色、激活态高亮、状态文案颜色均随系统主题切换；
  - 内容区使用 `innerPadding` 正确避让 TopAppBar。
- 验证结果：
  - `./gradlew :app:assembleDebug` 通过。
  - `./gradlew :app:testDebugUnitTest` 通过。

## 2026-03-24（主动作悬浮窗深浅模式 + 尺寸缩放）
- `FloatingWindowService` 悬浮窗主面板与点位气泡改为跟随系统深浅模式：
  - 根据 `isSystemInDarkTheme()` 选择 `Material3 darkColorScheme/lightColorScheme`；
  - 面板、按钮、动作行、点位气泡颜色按主题分别取色，避免暗色模式下对比不足。
- 主动作悬浮窗尺寸缩放：
  - 新增 `FLOATING_PANEL_SCALE_FACTOR = 0.5f`；
  - 面板内部布局尺寸（圆角、padding、按钮、动作列表区域、间距）统一按 `50%` 缩放。
- 验证结果：
  - `./gradlew :app:assembleDebug` 通过。
  - `./gradlew :app:testDebugUnitTest` 通过。

## 2026-03-24（启动即请求存储权限）
- `MainActivity` 新增启动阶段存储权限请求：
  - 应用打开后立即发起系统权限请求，不增加额外业务提示弹窗；
  - 运行时按系统版本请求：
    - Android 13+：`READ_MEDIA_IMAGES`、`READ_MEDIA_VIDEO`、`READ_MEDIA_AUDIO`
    - Android 12 及以下：`READ_EXTERNAL_STORAGE`（Android 9 及以下同时请求 `WRITE_EXTERNAL_STORAGE`）
  - 触发时机调整为 `onResume` 首次触发，避免部分机型在冷启动首帧阶段丢失权限弹窗。
- `AndroidManifest.xml` 同步声明新旧存储权限（旧权限保留 `maxSdkVersion` 约束）。
- 验证结果：
  - `./gradlew :app:assembleDebug` 通过。
  - `./gradlew :app:testDebugUnitTest` 通过。

## 2026-03-24（主动作悬浮窗横向收窄 + 文本单行自适应）
- 主动作悬浮窗完整态横向长度收窄：
  - 保持整体缩放系数 `FLOATING_PANEL_SCALE_FACTOR = 1f`；
  - 新增横向专用缩放 `FLOATING_PANEL_HORIZONTAL_SCALE_FACTOR = 0.5f`；
  - 对完整态面板的横向关键尺寸（内边距、左右区间距、动作列表列宽）按 50% 缩放，降低横向占用。
- 悬浮窗文本显示策略调整：
  - 新增 `AutoResizeSingleLineText`，统一用于面板标题、动作列表标题、空态提示、动作项名称等文本；
  - 文本强制单行显示，显示不下时自动逐步缩小字号（到最小阈值）并启用省略号，避免换行挤压布局。
- 验证结果：
  - `./gradlew :app:assembleDebug --no-daemon` 通过。

## 2026-03-24（主动作悬浮窗极简按钮列 + 滑动双端点可视编辑）
- 主动作悬浮窗完整态改为极简结构：
  - 仅保留左侧竖排按钮列（运行/录制/添加/删除最新/保存/最小化）；
  - 保留顶部“小白条”作为拖动区域；
  - 移除右侧动作列表区域，降低遮挡。
- 点击点悬浮气泡缩放：
  - 点位气泡尺寸统一缩小到原来的 50%（`POINT_BUBBLE_SCALE_FACTOR = 0.5f`）。
- 滑动动作点位可视编辑升级：
  - 滑动动作不再只有单点；改为起点与终点两个独立可拖动气泡；
  - 文案显示为 `n.滑动(起)` 与 `n.滑动(终)`；
  - 新增蓝色细线实时连接起点与终点；
  - 拖动起点仅更新 `x/y`，拖动终点仅更新 `endX/endY`，用于精确编辑滑动路径。
- 验证结果：
  - `./gradlew :app:assembleDebug --no-daemon` 通过。

## 2026-03-24（点位标签精简 + 气泡尺寸上调）
- 点位标签样式调整：
  - 点击动作中心文本仅显示序号数字（如 `2`）；
  - 滑动动作改为 `2起` / `2终` 两个端点标识。
- 点位气泡尺寸调整：
  - 在“已改为半尺寸”基础上上调为原始尺寸的 `75%`（`POINT_BUBBLE_SCALE_FACTOR = 0.75f`）。
- 验证结果：
  - `./gradlew :app:assembleDebug --no-daemon` 通过。

## 2026-03-24（点位文字居中与铺满原点）
- 点位气泡文字排版优化：
  - 文本改为强制居中（`TextAlign.Center`），确保 `2 / 2起 / 2终` 在圆心视觉居中；
  - 气泡文本改为“超大基准字号 + 自动收缩到刚好可显示”，使文字尽量铺满原点；
  - 文本内边距进一步缩小，减少空白边距。
- 自适应精度调整：
  - 自动缩字号步进由 `0.5sp` 调整为 `0.25sp`，匹配更贴边的铺满效果。
- 验证结果：
  - `./gradlew :app:assembleDebug --no-daemon` 通过。

## 2026-03-24（点位文字缩放判定修复）
- 修复点位圆心文本未正确缩放导致出现 `...` 的问题：
  - `AutoResizeSingleLineText` 溢出检测从 `didOverflowWidth` 改为 `hasVisualOverflow`，确保在真实可视溢出时继续缩字号；
  - 为点位文本增加 `overflow` 参数并在气泡中使用 `TextOverflow.Clip`，避免圆心出现省略号；
  - 点位文本最小字号下限下调为 `5sp`，保证 `2 / 2起 / 2终` 在小气泡里仍可完整显示。
- 验证结果：
  - `./gradlew :app:assembleDebug --no-daemon` 通过。

## 2026-03-24（移除最小化功能，恢复关闭X）
- `FloatingWindowService` 移除最小化逻辑：
  - 删除 `panelMinimized` 状态与最小化 UI 分支；
  - 删除 `FloatingWindowMode` 与 `setMode/mode` 对外状态流。
- 主面板末位按钮改回关闭行为：
  - 末位按钮由“最小化（缩）”改为 `X` 图标；
  - 点击后执行关闭服务（并回滚未保存改动），Toast 提示“动作悬浮窗已关闭”。
- 首页配置同步清理：
  - 移除“悬浮窗模式（编辑/运行）”下拉与相关文案，避免展示已删除功能。
- 验证结果：
  - `./gradlew :app:assembleDebug --no-daemon` 通过。

## 2026-03-24（主页点击点列表支持编辑与删除）
- `AutoClickScreen` 的“点击点列表”新增两个操作按钮：
  - `编辑点击点`：在主页直接弹出编辑窗口，复用现有点位更新逻辑（`AutoClickCoordinator.updatePointConfig`）；
  - `删除点击点`：直接调用 `AutoClickCoordinator.removePoint(pointId)` 并提示结果。
- 编辑窗口能力：
  - 支持修改中心坐标、延迟、触摸时长、重复次数；
  - 对滑动动作额外支持终点坐标（`endX/endY`）编辑；
  - 保存后立即刷新列表展示。
- 验证结果：
  - `./gradlew :app:assembleDebug --no-daemon` 通过。

## 2026-03-24（应用图标替换为 icon.jpg）
- 将项目根目录 `icon.jpg` 拷贝到应用资源目录：
  - `app/src/main/res/drawable/icon.jpg`
- `AndroidManifest.xml` 图标引用更新：
  - `android:icon` 从 `@mipmap/ic_launcher` 改为 `@drawable/icon`
  - `android:roundIcon` 从 `@mipmap/ic_launcher_round` 改为 `@drawable/icon`
- 验证结果：
  - `./gradlew :app:assembleDebug --no-daemon` 通过。

## 2026-03-25（关于页图标/外链按钮 + 动作参数编辑自动保存）
- 关于页（`AboutScreen`）调整：
  - 顶部图标改为 `R.drawable.icon`（与当前应用图标一致）；
  - 保持原有列表样式，在列表中新增“官网/QQ群”两项并支持外链跳转：
    - 官网：`https://littlecold.cn/`
    - QQ群：`https://qm.qq.com/q/vTyFd6Fsti`
- 动作参数编辑后自动保存：
  - 悬浮窗编辑动作参数保存时，`updatePointConfig` 后自动执行 `AutoClickCoordinator.saveProfile()`；
  - 首页“点击点列表”编辑动作参数保存时，`updatePointConfig` 后自动执行 `AutoClickCoordinator.saveProfile()`；
  - 两处均增加成功/失败 Toast 提示。
- 验证结果：
  - `./gradlew :app:assembleDebug --no-daemon` 通过。

## 2026-03-25（定时触发跟随运行方式 + 音量下强制停止）
- 定时触发与运行方式：
  - 定时任务触发后继续使用当前自动点击配置中的 `runMode`（运行一次 / 循环运行）执行；
  - 运行状态文案增加运行方式提示，定时触发时显示“按运行方式执行”。
- 音量下强制停止：
  - `AutoClickAccessibilityService` 新增按键事件处理，运行/暂停状态下按“音量下键”立即停止任务；
  - 停止后状态提示为“检测到音量下键，已强制停止”。
  - 无障碍配置新增键盘事件过滤能力：
    - `accessibility_service_config.xml` 增加 `flagRequestFilterKeyEvents`；
    - 服务连接时同步设置 `AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS`，提升机型兼容性。
- 验证结果：
  - `./gradlew :app:assembleDebug --no-daemon` 通过。

## 2026-03-25（音量下强制停止修复：补全按键过滤能力声明）
- 修复原因：
  - 部分系统上仅设置 `flagRequestFilterKeyEvents` 不足以接收按键事件；
  - 需要在无障碍配置中显式声明 `canRequestFilterKeyEvents="true"`。
- 修复内容：
  - `accessibility_service_config.xml` 新增：
    - `android:canRequestFilterKeyEvents="true"`
- 验证结果：
  - `./gradlew :app:assembleDebug --no-daemon` 通过。

## 2026-03-25（发布签名接入 + Release 打包）
- 新增发布签名资产（按用户要求可放仓库）：
  - `keystore/littleclicker-release.jks`
  - `keystore/release-signing.properties`
- `app/build.gradle.kts` 接入发布签名：
  - 读取 `keystore/release-signing.properties`。
  - `signingConfigs.create("release")` 配置 `storeFile / storePassword / keyAlias / keyPassword`。
  - `buildTypes.release` 绑定 `signingConfig = signingConfigs.getByName("release")`。
- 构建与验签结果：
  - `./gradlew :app:assembleRelease :app:bundleRelease` 通过。
  - `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk` 通过（证书 `CN=LittleClicker`）。
  - `jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab` 通过（证书 `CN=LittleClicker`）。
- 产物已复制到仓库可提交目录：
  - `releases/2026-03-25/LittleClicker-v1.0-release.apk`
  - `releases/2026-03-25/LittleClicker-v1.0-release.aab`
  - `releases/2026-03-25/checksums.txt`

## 2026-03-25（自动点击坐标修复：对齐悬浮圆点中心）
- 问题现象：
  - 部分机型上，自动点击命中点相对悬浮圆点存在“向上偏移”。
- 修复方案：
  - 增加“悬浮窗坐标 -> 屏幕坐标”偏移同步：
    - `FloatingWindowService` 在面板创建/移动后读取 `view.getLocationOnScreen`，计算并上报窗口偏移；
    - `AutoClickCoordinator` 新增偏移存储与坐标转换接口 `toScreenCoordinateX/Y`。
  - 执行动作前统一转换：
    - `AutoClickAccessibilityService` 在点击/滑动派发前，先将点位与终点转换为屏幕绝对坐标再执行手势。
- 效果：
  - 自动点击与悬浮圆点中心对齐，滑动起终点也按同一坐标系执行。
- 验证结果：
  - `./gradlew :app:assembleDebug --no-daemon` 通过。

## 2026-03-25（悬浮窗类型切换为 TYPE_ACCESSIBILITY_OVERLAY）
- 变更目标：
  - 已授予无障碍权限时，悬浮窗窗口类型优先使用 `TYPE_ACCESSIBILITY_OVERLAY`，降低在 Chrome 等应用中的“untrusted touch”拦截风险。
- 实现方式：
  - `AutoClickAccessibilityService` 新增 `isConnected()`，用于判断无障碍服务是否已连接；
  - `FloatingWindowService` 与 `TimerFloatingWindowService` 的 `createLayoutParams` 改为动态选择窗口类型：
    - 无障碍已连接：`TYPE_ACCESSIBILITY_OVERLAY`
    - 否则回退：`TYPE_APPLICATION_OVERLAY`
    - 低版本保留 `TYPE_PHONE` 兼容分支。
- 验证结果：
  - `./gradlew :app:compileDebugKotlin --no-daemon` 通过。
  - `./gradlew :app:assembleDebug --no-daemon` 通过。

## 2026-03-25（修复 TYPE_ACCESSIBILITY_OVERLAY 的 BadToken 闪退）
- 问题现象：
  - 启动动作悬浮窗时抛出 `WindowManager$BadTokenException: token null is not valid`。
  - 根因是 `FloatingWindowService`/`TimerFloatingWindowService` 直接以普通 Service 上下文执行 `addView(TYPE_ACCESSIBILITY_OVERLAY)`。
- 修复方案：
  - 新增无障碍服务托管窗口接口（`AutoClickAccessibilityService`）：
    - `addOverlayView(view, params)`
    - `updateOverlayView(view, params)`
    - `removeOverlayView(view)`
  - 动作悬浮窗与定时悬浮窗改为“优先委托无障碍服务加窗”：
    - 成功则由无障碍服务维护窗口；
    - 若委托失败则自动回退到 `TYPE_APPLICATION_OVERLAY`，避免闪退。
  - 动作悬浮窗新增 `viewHosts` 追踪每个 view 的托管方（无障碍服务 / 系统悬浮窗），更新与移除走对应宿主。
- 验证结果：
  - `./gradlew :app:compileDebugKotlin --no-daemon` 通过。
  - `./gradlew :app:assembleDebug --no-daemon` 通过。

## 2026-03-25（运行稳定性修复：滑动中停止与手动触控干预）
- 问题1：执行滑动动作时点击停止，无法稳定按“正常停止”结束。
- 问题2：运行过程中用户触摸屏幕，会触发 `dispatchGesture.onCancelled`，任务直接失败停止。
- 修复内容（`AutoClickAccessibilityService`）：
  - 新增 `stopRequested` 标志，`stopExecution()` 时置位；
  - 手势分发结果从 `Boolean` 升级为 `GestureDispatchResult`（`Completed/Cancelled/FailedToStart`）；
  - 对 `Cancelled` 分支改为：
    - 若为主动停止或协程已取消 => 按取消流程退出（Idle）；
    - 否则仅跳过当前动作，不再将整轮任务判定失败。
  - `FailedToStart` 仍按异常失败处理（避免静默掩盖真实故障）。
- 预期效果：
  - 滑动过程中点停止会走正常停止路径，不再误入失败状态；
  - 用户手动触摸屏幕不会导致自动点击任务整体退出。
- 验证结果：
  - `./gradlew :app:compileDebugKotlin --no-daemon` 通过。
  - `./gradlew :app:assembleDebug --no-daemon` 通过。

## 2026-03-25（启动检查更新 + 顶部更新卡片）
- 新增版本检查模块：
  - 新增 `AppUpdateChecker`，应用启动时请求 `https://littlecold.cn/littleclicker/version.txt`；
  - 解析格式：`版本号|下载链接|更新日志`（`|` 分割，第三段使用 `limit = 3` 兼容日志内分隔符）。
- 启动检查接入：
  - `MainActivity` 在 `AppRoot` 首次 `LaunchedEffect` 中执行检查；
  - 使用本地 `BuildConfig.VERSION_CODE` 与线上版本号比较，仅在线上版本更高时返回更新信息。
- 首页顶部提示：
  - `AutoClickScreen` 新增可选更新信息入参；
  - 有更新时在页面顶部显示 miuix 强调色卡片；
  - 主文案：`检测到更新！点击下载`，副文案展示更新日志；
  - 点击卡片通过系统浏览器打开下载链接。
- 验证结果：
  - `./gradlew :app:assembleDebug --no-daemon` 通过。
  - `./gradlew :app:testDebugUnitTest --no-daemon` 通过。
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk` 安装成功。
  - `adb logcat -c` 清空日志后执行 `adb shell monkey -p com.example.littleclicker -c android.intent.category.LAUNCHER 1` 成功拉起应用。
  - `adb logcat -d` 未检出 `FATAL EXCEPTION` / `Process: com.example.littleclicker` 崩溃日志。

## 2026-03-25（更新卡片位置微调）
- UI 调整：
  - 更新提示卡片从页面最顶部下移；
  - 现在位置为“标题介绍”之后、“权限设置”之前（即权限设置上方）。
- 验证结果：
  - `./gradlew :app:assembleDebug --no-daemon` 通过。

## 2026-03-25（公告功能：启动拉取 + 顶部卡片）
- 新增公告检查模块：
  - 新增 `AppNoticeChecker`，启动时请求 `https://littlecold.cn/littleclicker/notice.txt`。
  - 解析格式：`链接|内容`（`|` 分割，第二段内容支持空格与中文）。
- 启动接入：
  - `MainActivity` 启动时并行检查“更新 + 公告”，并传入首页。
- 首页展示：
  - `AutoClickScreen` 顶部新增公告卡片（与更新卡片同样样式）：
    - 主文案：`公告通知，点击查看`
    - 副文案：公告内容
  - 点击公告卡片后通过系统浏览器打开公告链接。
- 验证结果：
  - `./gradlew :app:assembleDebug --no-daemon` 通过。
  - `./gradlew :app:testDebugUnitTest --no-daemon` 通过。
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk` 安装成功。
  - `adb logcat -c` 清空日志后执行 `adb shell monkey -p com.example.littleclicker -c android.intent.category.LAUNCHER 1` 成功拉起应用。
  - `adb logcat -d` 未检出 `FATAL EXCEPTION` / `Process: com.example.littleclicker` 崩溃日志。

## 2026-03-25（录制后自动模拟动作）
- 需求实现：
  - 用户每录制完成一个动作后，立即自动模拟执行该动作，便于继续录制下一步。
- 代码改动：
  - `AutoClickAccessibilityService` 新增单动作回放入口（复用现有点击/滑动手势派发逻辑），录制回放触发时延约 `80ms`。
  - `FloatingWindowService` 在录制成功后调用单动作回放。
  - 新增录制输入忽略窗口：自动回放期间短暂忽略录制触摸采集，避免把模拟动作再次录入形成循环。
- 验证结果：
  - `./gradlew clean :app:assembleDebug --no-daemon` 通过。
  - `./gradlew :app:testDebugUnitTest --no-daemon` 通过。
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk` 安装成功。
  - `adb logcat -c` 清空日志后执行 `adb shell monkey -p com.example.littleclicker -c android.intent.category.LAUNCHER 1` 成功拉起应用。
  - `adb logcat -d` 未检出 `FATAL EXCEPTION` / `Process: com.example.littleclicker` 崩溃日志。

## 2026-03-25（录制方式开关 + 录制穿透修复）
- 问题修复：
  - 录制成功后虽然提示“已录制”，但未对底层应用产生可见模拟点击。
- 新增录制方式：
  - 在“运行方式”下方新增 `录制方式` 下拉，选项：
    - `仅录制`
    - `录制时穿透到应用`
  - 配置已持久化到 `AutoClickProfile.recordingMode`，历史配置自动兼容默认到“录制时穿透到应用”。
- 穿透逻辑修复：
  - 录制为“穿透模式”时，每录制一条动作立即触发单动作回放；
  - 回放窗口内临时将录制层切到不可触摸，使模拟触摸透传到底层 App；
  - 同时维持输入忽略窗口，避免回放动作被重复录入。
- 验证结果：
  - `./gradlew clean :app:assembleDebug --no-daemon` 通过。
  - `./gradlew :app:testDebugUnitTest --no-daemon` 通过。
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk` 安装成功。
  - `adb logcat -c` 清空日志后执行 `adb shell monkey -p com.example.littleclicker -c android.intent.category.LAUNCHER 1` 成功拉起应用。
  - `adb logcat -d` 未检出 `FATAL EXCEPTION` / `Process: com.example.littleclicker` 崩溃日志。

## 2026-03-25（滑动模拟修复：按手势时长录制）
- 问题现象：
  - 录制滑动后回放效果不稳定，表现为“提示已录制，但滑动未正常模拟”。
- 修复方案：
  - 录制层不再固定使用 `50ms` 触摸时长；
  - 改为按真实手势按下-抬起时长记录，并对滑动设置最小时长下限（`220ms`）；
  - 回放入口也增加滑动最小时长保护，避免被系统识别为近似点击。
- 验证结果：
  - `./gradlew clean :app:assembleDebug --no-daemon` 通过。
  - `./gradlew :app:testDebugUnitTest --no-daemon` 通过。
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk` 安装成功。
  - `adb logcat -c` 清空日志后执行 `adb shell monkey -p com.example.littleclicker -c android.intent.category.LAUNCHER 1` 成功拉起应用。
  - `adb logcat -d` 未检出 `FATAL EXCEPTION` / `Process: com.example.littleclicker` 崩溃日志。

## 2026-03-25（短滑动与跨点位滑动回放修复）
- 问题现象：
  - 慢速长滑动可回放，但快速短滑动与路径经过悬浮点位时回放失败。
- 修复方案：
  - 短滑动识别优化：滑动判定阈值从“仅距离 24px”改为“距离阈值 + 快速短滑动判定”组合规则；
  - 回放时长策略优化：滑动最小时长下调，避免快速短滑动被强制拉慢；
  - 遮挡修复：新创建的点位覆盖层在录制态下即初始化为不可触摸，避免“刚录完就回放”时被新点位短暂遮挡。
- 验证结果：
  - `./gradlew clean :app:assembleDebug --no-daemon` 通过。
  - `./gradlew :app:testDebugUnitTest --no-daemon` 通过。
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk` 安装成功。
  - `adb logcat -c` 清空日志后执行 `adb shell monkey -p com.example.littleclicker -c android.intent.category.LAUNCHER 1` 成功拉起应用。
  - `adb logcat -d` 未检出 `FATAL EXCEPTION` / `Process: com.example.littleclicker` 崩溃日志。

## 2026-03-25（配置自动保存每 1 秒 + 移除悬浮窗保存按钮）
- 配置自动保存改为“应用运行即自动执行”：
  - 在 `AutoClickCoordinator` 新增自动保存循环任务，`initialize()` 后每 `1000ms` 自动落盘当前配置；
  - 自动保存与手动保存共用保存锁（`saveLock`），避免并发写入配置文件冲突。
- 启动初始化补强：
  - 新增 `LittleClickerApplication`，在 `Application.onCreate()` 中调用 `AutoClickCoordinator.initialize(this)`；
  - `AndroidManifest.xml` 为 `<application>` 增加 `android:name=".LittleClickerApplication"`。
- 悬浮窗侧栏精简：
  - `FloatingWindowService` 删除“保存”按钮（保留运行/录制/添加/删除最新/关闭）。
- 验证结果：
  - `./gradlew :app:assembleDebug --no-daemon` 通过。
  - `./gradlew :app:testDebugUnitTest --no-daemon` 通过。
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk` 安装成功。
  - `adb logcat -c` 清空日志后执行 `adb shell monkey -p com.example.littleclicker -c android.intent.category.LAUNCHER 1` 成功拉起应用。
  - `adb logcat -d` 未检出 `FATAL EXCEPTION` / `Process: com.example.littleclicker` 崩溃日志。
  - 实机验证自动保存：读取 `files/autoclick/profiles/default.json` 的 `updatedAt`，间隔约 2 秒后再次读取，时间差约 `2005ms`，符合持续自动保存预期。

## 2026-03-25（Release 混淆修复：配置持久化相关 keep 规则加固）
- 问题背景：
  - 用户反馈 release 包在开启混淆后，配置保存/读取表现异常，怀疑与 R8 混淆导致的 Gson 反射不兼容有关。
- 修复内容（`app/proguard-rules.pro`）：
  - 增加 `-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*`；
  - 对配置持久化模型与 payload 由“仅保留字段”升级为“整类保留”：
    - `AutoClickPoint`
    - `AutoClickProfile`
    - `AutoClickRepository$AutoClickStorageState`
    - `AutoClickRepository$AutoClickPointPayload`
    - `AutoClickRepository$AutoClickProfilePayload`
  - 保留 JSON 使用的枚举：
    - `AutoClickActionType`
    - `AutoClickRunMode`
- 验证结果：
  - `./gradlew :app:assembleRelease --no-daemon` 通过。
  - `adb install -r app/build/outputs/apk/release/app-release.apk` 安装成功。

## 2026-03-25（录制滑动延迟 200ms 再模拟）
- 需求实现：
  - 录制模式下，用户完成滑动动作后不再立即回放，改为等待 `200ms` 再模拟该滑动。
- 代码改动：
  - `FloatingWindowService` 按动作类型区分回放触发延迟：
    - 点击维持 `80ms`
    - 滑动改为 `200ms`
  - `AutoClickAccessibilityService.replayRecordedAction` 新增可传入 `triggerDelayMs` 参数，供录制层按动作类型指定延迟。
  - `FloatingWindowService.armRecordReplayPassThroughWindow` 增加 `triggerDelayMs` 参数，穿透窗口时长改为“触发延迟 + 手势时长 + 额外缓冲”，避免等待阶段结束后录制层过早恢复触摸拦截。
- 验证结果：
  - `./gradlew :app:assembleDebug --no-daemon` 通过。
  - `./gradlew :app:testDebugUnitTest --no-daemon` 通过。
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk` 安装成功。
  - `adb logcat -c` 清空日志后执行 `adb shell monkey -p com.example.littleclicker -c android.intent.category.LAUNCHER 1` 成功拉起应用。
  - `adb logcat -d | Select-String "FATAL EXCEPTION|AndroidRuntime|Process: com.example.littleclicker"` 未发现 LittleClicker 进程崩溃（仅有 monkey 命令自身 `AndroidRuntime` 启停日志）。

## 2026-03-25（长按编辑动作时悬浮层降底防遮挡）
- 需求实现：
  - 长按编辑动作时，将动作悬浮层临时降到底层，避免遮挡编辑输入框。
- 代码改动：
  - `FloatingWindowService` 新增编辑弹窗态控制：
    - 打开编辑框前将主面板与点位覆盖层统一设为不可触摸；
    - 同时将主面板与点位覆盖层透明度降为 `0.18`，避免视觉遮挡；
    - 关闭编辑框后自动恢复触摸与透明度。
  - `showPointEditDialog` 增加 `setOnDismissListener`，无论“取消/保存/异常关闭”都执行恢复逻辑，避免状态残留。
  - `setPointOverlaysTouchable` 增加编辑态保护：编辑期间强制不可触摸，防止被运行态/录制态状态刷新反向改回可触摸。
- 验证结果：
  - `./gradlew :app:assembleDebug --no-daemon` 通过。
  - `./gradlew :app:testDebugUnitTest --no-daemon` 通过。
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk` 安装成功。
  - `adb logcat -c` 清空日志后执行 `adb shell monkey -p com.example.littleclicker -c android.intent.category.LAUNCHER 1` 成功拉起应用。
  - `adb logcat -d | Select-String "FATAL EXCEPTION|AndroidRuntime|Process: com.example.littleclicker"` 未发现 LittleClicker 进程崩溃（仅有 monkey 命令自身 `AndroidRuntime` 启停日志）。

## 2026-03-25（编辑动作时悬浮层完全隐藏）
- 需求实现：
  - 用户进入动作编辑框时，悬浮窗与点位层不应可见。
- 代码改动：
  - `FloatingWindowService` 将编辑态透明度常量从 `0.18` 调整为 `0`，实现编辑期间“完全不可见”。
  - 保留原有“不可触摸 + 关闭编辑框后自动恢复”的逻辑不变。
- 验证结果：
  - `./gradlew :app:assembleDebug --no-daemon` 通过。
  - `./gradlew :app:testDebugUnitTest --no-daemon` 通过。
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk` 安装成功。
  - `adb logcat -c` 清空日志后执行 `adb shell monkey -p com.example.littleclicker -c android.intent.category.LAUNCHER 1` 成功拉起应用。
  - `adb logcat -d | Select-String "FATAL EXCEPTION|AndroidRuntime|Process: com.example.littleclicker"` 未发现 LittleClicker 进程崩溃（仅有 monkey 命令自身 `AndroidRuntime` 启停日志）。

## 2026-03-25（自动保存体验修复：关闭悬浮窗前强制保存）
- 问题现象：
  - 已开启“每 1 秒自动保存”后，用户在改动动作后快速关闭悬浮窗，仍可能看到改动丢失。
- 根因分析：
  - 悬浮窗关闭路径仍在执行 `discardUnsavedChanges()`，会把“最近 1 秒内尚未被自动保存循环落盘”的改动回滚。
- 修复方案：
  - `FloatingWindowService` 的关闭路径（面板关闭按钮与 `ACTION_STOP`）改为“先 `saveProfile()` 再关闭服务”，移除关闭时回滚行为。
  - 面板关闭时若即时保存失败，额外弹出失败提示，避免误以为已保存成功。
- 验证结果：
  - `./gradlew :app:assembleDebug --no-daemon` 通过（构建期间 Kotlin 增量缓存出现已知告警，已自动回退并成功产物）。
  - `./gradlew :app:testDebugUnitTest --no-daemon` 通过（同上，已自动回退并成功）。
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk` 安装成功。
  - `adb shell run-as com.example.littleclicker cat files/autoclick/profiles/default.json` 连续读取两次（间隔 2 秒），`updatedAt` 从 `1774427173162` 增加到 `1774427175169`，自动保存在持续落盘。
  - `adb logcat -c` 清空日志后执行 `adb shell monkey -p com.example.littleclicker -c android.intent.category.LAUNCHER 1` 成功拉起应用。
  - `adb logcat -d | Select-String "FATAL EXCEPTION|AndroidRuntime|Process: com.example.littleclicker"` 未发现 LittleClicker 进程崩溃（仅有 monkey 命令自身 `AndroidRuntime` 启停日志）。

## 2026-04-05（录制动作回放一致性修复：点击时长与动作间隔）
- 问题现象：
  - 录制滑动/点击后，回放行为与实际录制手势不一致，尤其表现为点击触发节奏与录制时不一致。
- 根因分析：
  - 录制点击时长被固定为 `50ms`，未使用真实按下到抬起时长；
  - 录制动作延时按“上一次抬起 -> 本次抬起”计算，误包含了本次按压时长，导致回放节奏偏慢。
- 修复内容：
  - `FloatingWindowService`：
    - 录制点击动作时改为使用真实手势时长（最小 `1ms`）；
    - 在手势 `down` 时记录 wall-clock 时间，并在写入录制动作时传递给协调器。
  - `AutoClickCoordinator`：
    - `addRecordedAction` 新增 `actionStartAtMillis` 参数；
    - 动作 `delayMs` 改为按“上一次抬起 -> 本次按下”计算，避免将本次按压时长重复计入延时。
- 验证结果：
  - `./gradlew :app:compileDebugKotlin --no-daemon` 通过。
  - `./gradlew :app:assembleDebug --no-daemon` 通过。

## 2026-04-05（录制滑动速度对齐用户手势）
- 需求实现：
  - 录制滑动后，回放速度需要与用户实际滑动速度一致。
- 修复内容：
  - `FloatingWindowService` 录制滑动动作时长改为直接使用真实手势时长（最小 `1ms`），移除滑动最小时长强制拉长逻辑。
  - `AutoClickAccessibilityService.replayRecordedAction` 移除录制回放阶段对滑动时长的二次最小值钳制，改为按录制值原样回放。
- 验证结果：
  - `./gradlew :app:compileDebugKotlin --no-daemon` 通过。
  - `./gradlew :app:assembleDebug --no-daemon` 通过。

## 2026-04-05（录制首个动作默认延迟 100ms）
- 问题现象：
  - 每次开始录制后，第一个动作的 `delayMs` 被写入为 `0`，导致首点无法按预期延迟触发。
- 修复内容：
  - `AutoClickCoordinator.addRecordedAction` 中，当 `recordedCount == 0` 时，首个录制动作延迟由 `0L` 调整为 `100L`。
  - 新增常量 `FIRST_RECORDED_ACTION_DELAY_MS = 100L`，统一管理该默认值。
- 验证结果：
  - `./gradlew :app:compileDebugKotlin --no-daemon` 通过。

## 2026-04-05（循环运行模式新增每轮延迟输入与自动保存）
- 需求实现：
  - 在“动作悬浮窗与运行方式”中，当运行方式为“循环运行直至手动停止”时，提供可编辑输入框控制每次循环延迟。
- 修复内容：
  - 数据模型：
    - `AutoClickProfile` 新增 `loopIntervalDelayMs` 字段，默认值 `200ms`。
    - `AutoClickRepository` 增加 `loopIntervalDelayMs` 的 JSON 读写兼容；旧配置缺省时自动回填 `200ms`。
  - 业务逻辑：
    - `AutoClickCoordinator` 新增 `updateLoopIntervalDelay(...)`，并在配置归一化中保证值不为负数。
    - `AutoClickAccessibilityService` 在 `LoopUntilStopped` 模式下，每轮执行完成后按 `loopIntervalDelayMs` 等待，再进入下一轮。
  - UI（miuix）：
    - `AutoClickScreen` 在循环运行模式下展示 `TextField`（标题“每次循环延迟(ms)”）。
    - 输入仅允许数字，默认显示 `200`，修改后立即写入配置并调用 `saveProfile()` 自动保存。
- 验证结果：
  - `./gradlew :app:compileDebugKotlin --no-daemon` 通过。

## 2026-04-05（定时设置后自动开启定时悬浮窗 + 自动同步 NTP）
- 需求实现：
  - 在“定时点击”模块中，用户设置 `hh:mm:ss` 成功后自动开启定时悬浮窗，并自动发起 NTP 校时。
- 修复内容：
  - `AutoClickScreen`：
    - 在“选择时间”回调中，`scheduleAtHms(...)` 成功后自动尝试开启 `TimerFloatingWindowService`；
    - 若权限未满足，沿用统一权限闸门引导并提示。
  - `AutoClickCoordinator`：
    - `scheduleAtHms(...)` 调整为每次设置时间都执行 `syncNtpTime(force = true)`，确保自动触发校时。
- 验证结果：
  - `./gradlew :app:assembleDebug :app:testDebugUnitTest` 通过。
