# LittleClicker 实现记录

最后更新：2026-03-23

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
