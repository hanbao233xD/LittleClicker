# LittleClicker 模块说明

最后更新：2026-03-22

## 1. 构建模块（Gradle）
- 作用：管理 Android App 编译、依赖与 Compose 构建能力。
- 实现方法：
  - 文件：`app/build.gradle.kts`
  - 开启 `buildFeatures.compose = true`。
  - 接入 Compose BOM 与 `activity-compose`、`material3`、`ui-tooling`。
  - 接入页面导航：`androidx.navigation:navigation-compose`。
  - 接入第三方 UI：`top.yukonga.miuix.kmp:miuix-android:0.8.7`。
  - 接入 JSON：`com.google.code.gson:gson`。

## 2. 应用清单模块（Manifest）
- 作用：声明应用组件、系统权限、入口 Activity 与无障碍服务。
- 实现方法：
  - 文件：`app/src/main/AndroidManifest.xml`
  - 权限声明：
    - `SYSTEM_ALERT_WINDOW`（悬浮窗）
    - `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`（忽略电池优化请求）
  - 入口页面：`MainActivity`（`MAIN/LAUNCHER`）。
  - 无障碍服务：`AutoClickAccessibilityService`，绑定 `BIND_ACCESSIBILITY_SERVICE`，并引用服务配置 XML。

## 3. 导航与页面模块（MainActivity + Compose）
- 作用：提供应用主界面架构与三页底部导航。
- 实现方法：
  - 文件：`app/src/main/java/com/example/littleclicker/MainActivity.kt`
  - 使用 `Scaffold` 搭建根布局，底部使用 `NavigationBar`。
  - 使用 `NavHost` + `composable(route)` 管理页面切换。
  - 预置三个 Tab：`首页`、`脚本管理`、`关于`。

## 4. 首页权限引导模块（HomeScreen）
- 作用：给小白用户提供“保姆级”授权流程，确保自动点击运行条件齐全。
- 实现方法：
  - 文件：`app/src/main/java/com/example/littleclicker/MainActivity.kt`
  - 通过卡片展示 3 项能力状态：悬浮窗、无障碍、电池优化。
  - 卡片点击后分别跳转系统设置页面：
    - 悬浮窗：`ACTION_MANAGE_OVERLAY_PERMISSION`
    - 无障碍：`ACTION_ACCESSIBILITY_SETTINGS`
    - 电池优化：`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
  - 通过 `onResume` 刷新授权状态；全部授权后启用“开启悬浮窗”按钮。

## 5. 脚本管理模块（ScriptManageScreen）
- 作用：作为脚本列表、导入、启停能力的承载入口。
- 实现方法：
  - 文件：`app/src/main/java/com/example/littleclicker/MainActivity.kt`
  - 当前为占位实现，已在导航体系中完成接入，后续可扩展本地脚本存储和执行状态管理。

## 6. 关于模块（AboutScreen）
- 作用：展示应用品牌信息与辅助入口（更新、隐私、免责声明）。
- 实现方法：
  - 文件：`app/src/main/java/com/example/littleclicker/MainActivity.kt`
  - 居中展示图标、名称、版本号。
  - 通过列表卡片展示 `检查更新`、`隐私政策`、`免责声明` 入口。

## 7. 无障碍服务模块（Accessibility Service）
- 作用：承载未来自动点击手势与可访问性事件处理。
- 实现方法：
  - 服务类：`app/src/main/java/com/example/littleclicker/service/AutoClickAccessibilityService.kt`
- 服务配置：`app/src/main/res/xml/accessibility_service_config.xml`
- 目前为骨架实现，已满足注册与启用前置条件，后续可在 `onAccessibilityEvent` 中接入脚本执行逻辑。

## 8. 悬浮窗服务模块（Floating Window Service）
- 作用：提供可视化悬浮控制面板与多靶标拖拽定位能力。
- 实现方法：
  - 文件：`app/src/main/java/com/example/littleclicker/service/FloatingWindowService.kt`
  - 服务基类：`LifecycleService`。
  - 渲染方式：`WindowManager + ComposeView`，覆盖层类型 `TYPE_APPLICATION_OVERLAY`。
  - 面板能力：4 个入口按钮（录制、播放/暂停、设置、关闭）。
  - 靶标能力：支持多靶标添加、拖拽、删除；序号按添加顺序显示，删除后按列表顺序重排，避免重复序号。
  - 对外入口：`FloatingWindowService.start(context)` / `FloatingWindowService.stop(context)`。
  - 清单声明：`app/src/main/AndroidManifest.xml` 中注册 `FloatingWindowService`。

## 9. 资源与主题模块（res）
- 作用：统一应用图标、字符串、颜色和主题配置。
- 实现方法：
  - 目录：`app/src/main/res/`
  - 当前包含 launcher 图标资源、基础主题与字符串配置，可继续扩展 miuix 风格颜色体系。
