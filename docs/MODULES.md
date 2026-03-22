# LittleClicker 模块说明

最后更新：2026-03-23

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
  - 组件声明：
    - `MainActivity`
    - `AutoClickAccessibilityService`
    - `FloatingWindowService`

## 3. 导航壳模块（MainActivity）
- 作用：提供三 Tab 导航壳与应用启动入口。
- 实现方法：
  - 文件：`app/src/main/java/com/example/littleclicker/MainActivity.kt`
  - 三个 Tab：`自动点击`、`脚本管理`、`关于`。
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
  - 定时项：选择本地时间点 `startAtMillis`，支持清除定时与过期提示。
  - 运行项：开启悬浮窗、立即开始、暂停/继续、停止、保存配置。
  - 点击点列表：改为只读展示，参数编辑入口迁移至悬浮窗长按弹窗。

## 5. 自动点击数据模块（Models + Repository）
- 作用：定义自动点击/脚本草稿数据结构并提供本地 JSON 持久化。
- 实现方法：
  - 文件：
    - `app/src/main/java/com/example/littleclicker/autoclick/AutoClickModels.kt`
    - `app/src/main/java/com/example/littleclicker/autoclick/AutoClickRepository.kt`
  - 核心模型：
    - `AutoClickPoint`
    - `AutoClickProfile`
    - `ScriptDraft`
    - `AutoClickRunState`
  - 存储位置：`filesDir` 私有目录（`autoclick/profile.json`、`scripts/*.json`）。

## 6. 自动点击协调模块（AutoClickCoordinator）
- 作用：统一页面、悬浮窗、无障碍服务的状态与操作入口。
- 实现方法：
  - 文件：`app/src/main/java/com/example/littleclicker/autoclick/AutoClickCoordinator.kt`
  - 能力：
    - 管理 `profile/runtime/scriptDrafts` 的 `StateFlow`
    - 点击点增删拖拽与参数更新
    - 应用内可靠定时（进程存活时到点触发）
    - 启动/暂停/继续/停止执行
    - 自动点击配置保存、脚本草稿新建/覆盖保存/列表刷新

## 7. 无障碍执行模块（AutoClickAccessibilityService）
- 作用：执行自动点击手势队列，并支持并发保护与暂停/继续/停止。
- 实现方法：
  - 文件：`app/src/main/java/com/example/littleclicker/service/AutoClickAccessibilityService.kt`
  - 执行方式：`dispatchGesture` + 单点点击手势。
  - 队列规则：按点位顺序、每点重复、全局循环展开执行。
  - 生命周期：服务连接后注册实例；销毁时释放任务与协程。

## 8. 自动点击悬浮窗模块（FloatingWindowService）
- 作用：提供自动点击专用可拖拽悬浮面板与点位拖拽能力。
- 实现方法：
  - 文件：`app/src/main/java/com/example/littleclicker/service/FloatingWindowService.kt`
  - 渲染：`WindowManager + ComposeView`。
  - 面板按钮：添加点位、开始/暂停、保存、关闭。
  - 拖动能力：面板拖动与点位拖动均支持边界约束，避免移动到屏幕外。
  - 点位编辑：长按点位弹出编辑窗口，支持调整坐标、延迟、触摸时长、重复次数。
  - 点位：支持多点拖拽、删除，顺序按添加顺序稳定显示。
  - 对外入口：`startAutoClickOverlay(context)` / `stopAutoClickOverlay(context)`。

## 9. 脚本草稿管理模块（ScriptManageScreen）
- 作用：先落地脚本保存能力，不包含动作编辑/执行。
- 实现方法：
  - 文件：`app/src/main/java/com/example/littleclicker/ui/ScriptManageScreen.kt`
  - 支持：
    - 新建草稿并保存
    - 覆盖保存已选草稿
    - 列表展示与点击加载

## 10. 关于模块（AboutScreen）
- 作用：展示应用品牌信息和占位入口。
- 实现方法：
  - 文件：`app/src/main/java/com/example/littleclicker/ui/AboutScreen.kt`
  - 展示图标、名称、版本以及基础列表项。

## 11. UI 通用工具模块（UiHelpers）
- 作用：集中权限跳转、状态检测、日期时间选择与格式化。
- 实现方法：
  - 文件：`app/src/main/java/com/example/littleclicker/ui/UiHelpers.kt`
