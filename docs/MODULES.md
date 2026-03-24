# LittleClicker 模块说明

最后更新：2026-03-24

## 1. 构建模块（Gradle）
- 作用：管理 Android App 编译、依赖与 Compose 构建能力。
- 实现方法：
  - 文件：`app/build.gradle.kts`
  - 开启 `buildFeatures.compose = true`。
  - 接入 Compose BOM、`activity-compose`、`material3`、`navigation-compose`。
  - 接入第三方 UI：`top.yukonga.miuix.kmp:miuix-android`。
  - 接入 JSON：`com.google.code.gson:gson`。

## 2. 应用清单模块（Manifest）
- 作用：声明应用组件、系统权限、入口 Activity 与服务。
- 实现方法：
  - 文件：`app/src/main/AndroidManifest.xml`
  - 权限声明：
    - `SYSTEM_ALERT_WINDOW`（悬浮窗）
    - `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`（忽略电池优化请求）
    - `INTERNET`（NTP 校时）
  - 组件声明：
    - `MainActivity`
    - `AutoClickAccessibilityService`
    - `FloatingWindowService`
    - `TimerFloatingWindowService`

## 3. 导航壳模块（MainActivity）
- 作用：提供主导航壳与应用启动入口。
- 实现方法：
  - 文件：`app/src/main/java/com/example/littleclicker/MainActivity.kt`
  - 两个 Tab：`自动点击`、`关于`。
  - 默认首页：`自动点击`。
  - 在入口处初始化 `AutoClickCoordinator`，供页面和服务共享状态。

## 4. 自动点击页面模块（AutoClickScreen）
- 作用：提供自动点击配置、定时、运行控制与权限引导。
- 实现方法：
  - 文件：`app/src/main/java/com/example/littleclicker/ui/AutoClickScreen.kt`
  - 保留 3 项权限卡片：悬浮窗、无障碍、电池优化。
  - 配置项：
    - `profile.name`
    - `cycleCount`
    - 每个点击点的 `delayMs`、`touchDurationMs`、`repeatCount`
  - 定时项：
    - 自定义 `hh:mm:ss` 选择器（默认当前时分，秒默认 `00`）
    - 大字实时时钟显示 `HH:mm:ss.S`（1 位小数）
    - NTP 状态展示、NTP 服务器配置入口
    - 定时悬浮窗开关入口
    - 设定时间规则展示与过期提示
  - 运行项：开启悬浮窗、立即开始、暂停/继续、停止、保存配置。
  - 点击点列表：改为只读展示，参数编辑入口迁移至悬浮窗长按弹窗。

## 5. 自动点击数据模块（Models + Repository）
- 作用：定义自动点击数据结构并提供本地 JSON 持久化。
- 实现方法：
  - 文件：
    - `app/src/main/java/com/example/littleclicker/autoclick/AutoClickModels.kt`
    - `app/src/main/java/com/example/littleclicker/autoclick/AutoClickRepository.kt`
  - 核心模型：
    - `AutoClickPoint`
      - 新增动作类型：`Click` / `Swipe`
      - 滑动动作支持终点坐标：`endX` / `endY`
    - `AutoClickProfile`
      - 新增 `ntpServerHost`（默认 `ntp.aliyun.com`）
      - 新增 `scheduleRuleHms`（保存 `hh:mm:ss` 规则）
    - `AutoClickRunState`
    - `TimeSyncState`
  - 存储位置：`filesDir` 私有目录（`autoclick/profiles/*.json`、`autoclick/state.json`）。
  - 兼容策略：旧 JSON 缺失新增字段时自动回填默认值，不破坏历史配置。

## 6. 自动点击协调模块（AutoClickCoordinator）
- 作用：统一页面、悬浮窗、无障碍服务的状态与操作入口。
- 实现方法：
  - 文件：`app/src/main/java/com/example/littleclicker/autoclick/AutoClickCoordinator.kt`
  - 能力：
    - 管理 `profile/profiles/runtime/recording` 的 `StateFlow`
    - 管理 `timeSync`（NTP 校时状态）`StateFlow`
    - 点击点增删拖拽与参数更新
    - `scheduleAtHms(hour, minute, second)` 定时规则配置与触发
    - 轮询对齐时钟触发（20~50ms 级轮询）
    - NTP 校时（`syncNtpTime` / `updateNtpServer` / `currentAlignedNowMillis`）
    - 启动/暂停/继续/停止执行
    - 自动点击配置保存、加载、删除与列表刷新

## 7. 无障碍执行模块（AutoClickAccessibilityService）
- 作用：执行自动点击手势队列，并支持并发保护与暂停/继续/停止。
- 实现方法：
  - 文件：`app/src/main/java/com/example/littleclicker/service/AutoClickAccessibilityService.kt`
  - 执行方式：`dispatchGesture` + 单点点击/滑动手势。
  - 队列规则：按点位顺序、每点重复、全局循环展开执行。
  - 生命周期：服务连接后注册实例；销毁时释放任务与协程。

## 8. 自动点击悬浮窗模块（FloatingWindowService）
- 作用：提供自动点击专用可拖拽悬浮面板与点位拖拽能力。
- 实现方法：
  - 文件：`app/src/main/java/com/example/littleclicker/service/FloatingWindowService.kt`
  - 渲染：`WindowManager + ComposeView`。
  - 面板按钮顺序：运行、录制/停止录制、添加动作、删除最新动作、保存、关闭。
  - 录制能力：录制开启后采集屏幕点击并转为动作，停止录制后保留结果。
  - 动作展示：面板列表与画圈标签统一显示 `序号.动作类型`（如 `1.点击`、`2.滑动`）。
  - 动作编辑：列表内支持铅笔图标编辑、垃圾桶图标删除。
  - 保存策略：动作编辑与拖动定位只更新当前内存状态，需手动点击面板“保存”才写入本地配置文件。
  - 关闭策略：关闭悬浮窗（含面板关闭按钮与主页面开关关闭）会回滚未保存改动，并从已保存配置重载动作列表。
  - 拖动能力：面板拖动与点位拖动均支持边界约束，避免移动到屏幕外。
  - 点位编辑：长按点位弹出编辑窗口，支持调整坐标、延迟、触摸时长、重复次数。
  - 点位：支持多点拖拽、删除，顺序按添加顺序稳定显示。
  - 对外入口：`startAutoClickOverlay(context)` / `stopAutoClickOverlay(context)`。

## 9. 配置管理模块（ConfigManageActivity + ConfigManageScreen）
- 作用：管理自动点击配置（保存、另存、加载、删除）。
- 实现方法：
  - 文件：
    - `app/src/main/java/com/example/littleclicker/ConfigManageActivity.kt`
    - `app/src/main/java/com/example/littleclicker/ui/ConfigManageScreen.kt`
  - 支持：
    - 编辑当前配置名称与循环次数
    - 保存当前配置/另存为新配置
    - 复制现有配置（自动命名为 `原配置名_副本`）
    - 本地配置列表加载与删除

## 10. 关于模块（AboutScreen）
- 作用：展示应用品牌信息和占位入口。
- 实现方法：
  - 文件：`app/src/main/java/com/example/littleclicker/ui/AboutScreen.kt`
  - 展示图标、名称、版本以及基础列表项。

## 11. UI 通用工具模块（UiHelpers）
- 作用：集中权限跳转、状态检测、日期时间选择与格式化。
- 实现方法：
  - 文件：`app/src/main/java/com/example/littleclicker/ui/UiHelpers.kt`
  - 关键格式化：
    - `formatDateTime`
    - `formatHms`
    - `formatHmsWithTenths`

## 12. NTP 校时模块（SntpClient）
- 作用：通过 UDP 123 发起 SNTP 请求，计算时钟偏移和往返延迟。
- 实现方法：
  - 文件：`app/src/main/java/com/example/littleclicker/autoclick/SntpClient.kt`
  - 输出：`offsetMillis`、`delayMillis`、`serverHost`。
  - 失败兜底：由协调器回退到本机时间，不阻塞定时功能。

## 13. 定时悬浮窗模块（TimerFloatingWindowService）
- 作用：提供独立于动作编辑悬浮窗的定时状态浮窗。
- 实现方法：
  - 文件：`app/src/main/java/com/example/littleclicker/service/TimerFloatingWindowService.kt`
  - 渲染：`WindowManager + ComposeView`。
  - 样式：灰色半透明背景、圆角、可拖动。
  - 文案：单行显示 `HH:mm:ss.S`、`设定时间`、`运行状态`。
  - 对外入口：`start(context)` / `stop(context)` 与 `overlayVisible` 状态流。
