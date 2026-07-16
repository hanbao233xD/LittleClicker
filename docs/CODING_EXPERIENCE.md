# LittleClicker 代码经验沉淀

最后更新：2026-07-16

## 使用规则
1. 本文件专门记录“编码过程中的经验、踩坑与最佳实践”。
2. 每次完成重要开发后都追加一条，避免重复踩坑。

## 经验记录

### 2026-07-16
8. `Scaffold` 底栏与页面内边距叠加经验：
- 现象：页面已应用 `Scaffold` 提供的 `innerPadding` 后，再对内容容器统一调用 `padding(16.dp)`，会在底栏上方额外保留 `16dp` 空白。
- 原因：`innerPadding` 已负责避让底部导航栏，额外的统一底部边距会继续缩小内容区域。
- 处理：有底栏的页面按方向设置内容间距，只保留顶部和左右边距；底部是否留白应根据设计单独指定。

9. 首次协议与系统权限弹窗的时序经验：
- 现象：应用启动后立即弹出 Compose 协议窗口时，原有的延迟系统权限申请可能覆盖协议内容，导致用户无法完整阅读倒计时弹窗。
- 处理：保留业务初始化与联网时机，仅将系统权限请求改为“协议已接受后再调度”，并让窗口焦点与协议确认回调共用同一个幂等入口。
- 建议：需要覆盖任意 Activity 的协议类 UI 优先使用 miuix `WindowDialog`；接受状态可用 `filesDir` 下的版本化原子标记文件记录，未来协议升级时通过提升文件版本触发重新确认。

10. miuix 0.8.7 `WindowDialog` 返回事件兼容经验：
- 现象：直接使用 `WindowDialog` 会因缺少 `LocalNavigationEventDispatcherOwner` 在运行时崩溃；仅创建无输入的根 Dispatcher 虽可消除崩溃，但旧版 Android 的物理返回键不会触发关闭。
- 处理：在弹窗外层提供 `rememberNavigationEventDispatcherOwner(parent = null)`；Android 13+ 将弹窗窗口的 `OnBackInvokedOverlayInput` 接入 Dispatcher，旧版 Android 在弹窗根 View 上增加 `KEYCODE_BACK` 兼容处理。
- 建议：miuix 窗口级弹层必须在真机上同时验证启动、物理返回键和预测返回手势，不能只以编译通过作为完成标准。

11. 模态协议弹窗的遮罩行为经验：
- 现象：将 `WindowDialog.onDismissRequest` 直接绑定到 `finishAffinity()` 后，点击黑色遮罩会立即退出应用，用户感知与闪退一致。
- 处理：协议弹窗的 `onDismissRequest` 使用空回调，让遮罩只负责阻断底层操作；退出行为仅由明确的“取消”按钮和返回键触发。

12. Compose 长协议文案的资源管理经验：
- 现象：把长文本放在 `strings.xml` 中需要手动书写 `\n`，编辑和审核时难以直观看到真实段落结构。
- 处理：将正文保存为 UTF-8 `res/raw/*.txt`，通过 `bufferedReader(Charsets.UTF_8).readText()` 完整读取；Compose `Text` 会原样显示 LF 和 CRLF 换行。

13. miuix `Text` 渐变文字经验：
- 整段文字可通过 `TextStyle.copy(brush = ...)` 应用渐变；只有部分文字需要渐变时，使用 `AnnotatedString` 并为目标片段设置 `SpanStyle(brush = ...)`。
- 为浅色和深色主题分别配置渐变色，避免同一套颜色在不同背景下对比度不足。

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
