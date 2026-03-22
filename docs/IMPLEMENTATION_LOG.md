# LittleClicker 实现记录

最后更新：2026-03-22

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
- 编译验证：`./gradlew :app:assembleDebug` 通过。
