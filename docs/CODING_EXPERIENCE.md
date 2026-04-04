# LittleClicker 代码经验沉淀

最后更新：2026-04-05

## 使用规则
1. 本文件专门记录“编码过程中的经验、踩坑与最佳实践”。
2. 每次完成重要开发后都追加一条，避免重复踩坑。

## 经验记录
### 2026-04-05
6. AlertDialog 自定义表单的可达性经验：
- 现象：表单项较多时，直接 `setView(LinearLayout)` 在部分机型/字号下会出现底部字段不可见、不可编辑。
- 处理：统一改为 `ScrollView` 包裹表单容器（`LinearLayout`），并设置 `isFillViewport = true`，确保弹窗内容可滚动触达。
- 建议：后续新增“多字段编辑弹窗”时，默认采用“`ScrollView + 表单容器`”结构，避免重复出现可达性问题。

7. 扩展动作枚举时的联动经验：
- 现象：自动点击动作从 `Click/Swipe` 扩展到更多类型后，容易遗漏 `when` 分支、JSON 解析、编辑表单字段与悬浮点位渲染等联动点。
- 处理：本次统一补齐四类同步改动：`模型枚举与显示名`、`执行器分发`、`存储反序列化兼容`、`UI 按动作能力动态展示字段`。
- 建议：后续每次新增动作类型，优先按“模型 -> 执行 -> 存储 -> UI”清单逐项核对，并至少执行一次 `:app:assembleDebug` 兜底。

### 2026-03-22
1. Compose 图标类运行时崩溃排查：
- 现象：`NoClassDefFoundError: androidx.compose.material.icons.Icons`。
- 原因：使用了 `Icons.Default.*` 但未引入图标依赖。
- 处理：在 `app/build.gradle.kts` 添加 `androidx.compose.material:material-icons-extended`。

2. miuix 落地经验：
- 仅添加 `miuix-android` 依赖不等于“已使用 miuix”。
- 需要将关键 UI 组件切到 miuix 体系（如 `MiuixTheme`、`Scaffold`、`NavigationBar`、`Card`、`Button`、`Text`）。

3. 防回归建议：
- 每次依赖变更后执行 `:app:assembleDebug`，先保证可编译再继续功能开发。

### 2026-03-22（补充）
4. Compose `painterResource` 资源类型限制：
- 现象：`Only VectorDrawables and rasterized asset types are supported`。
- 触发点：`painterResource(R.mipmap.ic_launcher)`（Adaptive Icon XML）。
- 处理：改用普通 Vector/Raster 资源（如 `R.drawable.ic_launcher_foreground`）。

### 2026-03-23
5. Service 中 ComposeView 的生命周期绑定经验：
- 现象：悬浮窗面板可点击，但 `StateFlow` 驱动的列表和按钮图标不随状态变化刷新。
- 原因：`ComposeView` 绑定了 `LifecycleService` 作为 `ViewTreeLifecycleOwner`，在该场景下生命周期活跃度不足，导致重组不稳定。
- 处理：将 `ViewTreeLifecycleOwner` 切到服务内自维护的 `OverlaySavedStateOwner`，并将其生命周期置为 `RESUMED`；同时保留 `SavedStateRegistryOwner` 绑定。
