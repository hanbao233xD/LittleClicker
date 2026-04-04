# LittleClicker 模块说明

最后更新：2026-04-05

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
    - 存储权限（新旧兼容声明）：`READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` / `READ_MEDIA_AUDIO`
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
  - 启动时运行时权限：应用 `onResume` 首次触发存储权限请求（按 Android 版本分支请求新旧权限），不添加额外业务提示文案。

## 4. 自动点击页面模块（AutoClickScreen）
- 作用：提供自动点击配置、定时、运行控制与权限引导。
- 实现方法：
  - 文件：`app/src/main/java/com/example/littleclicker/ui/AutoClickScreen.kt`
  - 保留 3 项权限卡片：悬浮窗、无障碍、电池优化。
  - 深色适配：页面背景渐变、卡片容器、成功态/强调色、定时大时钟区域均支持深浅模式动态配色。
  - 配置项：
    - `profile.name`
    - `cycleCount`
    - `runMode`（`运行一次` / `循环运行直至手动停止`）
    - `loopIntervalDelayMs`（循环模式下每轮结束后的等待时长，默认 `200ms`）
    - `recordingMode`（`仅录制` / `录制时穿透到应用`）
    - 每个点击点的 `delayMs`、`touchDurationMs`、`repeatCount`
  - 定时项：
    - 自定义 `hh:mm:ss` 选择器（默认当前时分，秒默认 `00`）
    - 大字实时时钟显示 `HH:mm:ss.S`（1 位小数）
    - NTP 状态展示、NTP 服务器配置入口
    - 定时悬浮窗开关入口
    - 设定时间规则展示与过期提示
  - 运行项：开启悬浮窗、立即开始、暂停/继续、停止、保存配置。
  - 悬浮窗启动闸门：动作悬浮窗与定时悬浮窗启动前统一检查 `悬浮窗/无障碍/电池优化` 三项权限，缺失时自动跳转对应设置页并提示，阻止启动。
  - 动作列表：支持 `点击/滑动/Home/Back/多任务` 添加、编辑与删除；编辑弹窗调用现有 `updatePointConfig` 更新逻辑，删除调用 `removePoint`。

## 5. 自动点击数据模块（Models + Repository）
- 作用：定义自动点击数据结构并提供本地 JSON 持久化。
- 实现方法：
  - 文件：
    - `app/src/main/java/com/example/littleclicker/autoclick/AutoClickModels.kt`
    - `app/src/main/java/com/example/littleclicker/autoclick/AutoClickRepository.kt`
  - 核心模型：
    - `AutoClickPoint`
      - 动作类型：`Click` / `Swipe` / `Home` / `Back` / `Recents`
      - 仅 `Click` / `Swipe` 使用屏幕坐标
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
    - 定时触发执行时使用“当前配置快照”的运行方式（运行一次/循环运行）
    - NTP 校时（`syncNtpTime` / `updateNtpServer` / `currentAlignedNowMillis`）
    - 启动/暂停/继续/停止执行
    - 自动点击配置保存、加载、删除与列表刷新

## 7. 无障碍执行模块（AutoClickAccessibilityService）
- 作用：执行自动点击手势队列，并支持并发保护与暂停/继续/停止。
- 实现方法：
  - 文件：`app/src/main/java/com/example/littleclicker/service/AutoClickAccessibilityService.kt`
  - 执行方式：`dispatchGesture`（点击/滑动）+ `performGlobalAction`（Home/Back/多任务）。
  - 录制回放：提供“单动作回放”入口，供录制流程在每次记录后立即模拟刚录制动作。
  - 队列规则：按点位顺序、每点重复、全局循环展开执行。
  - 运行中热键：支持按下“音量下键”强制停止当前自动点击任务。
  - 运行态提示：自动点击运行期间在屏幕底部展示透明红色提示文案（`按音量下键可强制停止`），提示层为不可触摸悬浮层，不影响底层点击。
  - 生命周期：服务连接后注册实例；销毁时释放任务与协程。

## 8. 自动点击悬浮窗模块（FloatingWindowService）
- 作用：提供自动点击专用可拖拽悬浮面板与点位拖拽能力。
- 实现方法：
  - 文件：`app/src/main/java/com/example/littleclicker/service/FloatingWindowService.kt`
  - 渲染：`WindowManager + ComposeView`。
  - 面板按钮顺序：运行、录制/停止录制、添加动作、删除最新动作、关闭（X）。
- 录制能力：录制开启后采集屏幕点击并转为动作，停止录制后保留结果。
  - 录制联动：每次成功录制一个动作后，自动模拟执行该动作，便于用户继续录制下一步。
  - 滑动联动延迟：录制到滑动动作后，自动模拟前先等待 `200ms`，降低系统与悬浮层状态切换导致的短滑动回放失败。
  - 时长策略：录制滑动时按真实手势时长记录，并设置最小时长下限，提升滑动回放稳定性。
  - 短滑动策略：对“快速短滑动”增加判定分支，避免被误识别为点击。
  - 穿透机制：录制回放窗口内临时将录制层设为不可触摸，使模拟动作透传到底层应用。
  - 防重复录制：自动模拟执行期间启用短暂输入忽略窗口，避免把模拟动作再次录进动作列表。
  - 覆盖层策略：录制态下新建点位覆盖层默认不可触摸，降低回放时路径被新点位短暂遮挡的概率。
  - 非坐标动作策略：`Home` / `Back` / `多任务` 不渲染屏幕点位气泡，仅在动作列表中展示与编辑。
  - 动作展示：面板列表与画圈标签统一显示 `序号.动作类型`（如 `1.点击`、`2.滑动`）。
  - 动作编辑：列表内支持铅笔图标编辑、垃圾桶图标删除。
  - 编辑防遮挡：长按进入“编辑动作”时，临时将动作悬浮层完全隐藏（透明度 `0`）且不可触摸；编辑框关闭后自动恢复。
  - 保存策略：`AutoClickCoordinator` 后台每 `1000ms` 自动保存当前配置；关闭悬浮窗前会再执行一次即时保存，减少最后 1 秒窗口内变更丢失。
- 关闭策略：关闭悬浮窗（含面板关闭按钮与主页面开关关闭）前先保存当前配置，再停止服务；不再执行“关闭即回滚未保存改动”。
  - 主题策略：主面板与点位气泡跟随系统深浅模式切换（Material3 `darkColorScheme/lightColorScheme` + 主题化颜色）。
  - 尺寸策略：主面板内布局按 `FLOATING_PANEL_SCALE_FACTOR = 0.5` 缩放（按钮、列表、间距、圆角统一缩放）。
  - 拖动能力：面板拖动与点位拖动均支持边界约束，避免移动到屏幕外。
  - 点位编辑：支持悬浮窗长按点位编辑，也支持主页点击点列表进入编辑弹窗（坐标/延迟/触摸时长/重复次数）。
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
  - 顶栏：使用 `miuix TopAppBar`（左上角返回导航图标），由 `Scaffold` 统一承载。
  - 深色适配：页面背景、卡片底色、激活态高亮与状态文字颜色随系统深浅模式自动切换。

## 10. 关于模块（AboutScreen）
- 作用：展示应用品牌信息和占位入口。
- 实现方法：
  - 文件：`app/src/main/java/com/example/littleclicker/ui/AboutScreen.kt`
  - 展示图标（`drawable/icon`）、名称、版本。
  - 保持原有列表样式，并在列表项中接入外链：
    - 官网：`https://littlecold.cn/`
    - QQ群加群链接：`https://qm.qq.com/q/vTyFd6Fsti`

## 11. UI 通用工具模块（UiHelpers）
- 作用：集中权限跳转、状态检测、日期时间选择与格式化。
- 实现方法：
  - 文件：`app/src/main/java/com/example/littleclicker/ui/UiHelpers.kt`
  - 关键格式化：
    - `formatDateTime`
    - `formatHms`
    - `formatHmsWithTenths`
  - 启动前置检查：
    - `ensureOverlayStartPermissions`

## 14. 动作参数自动保存规则
- 作用：在“编辑动作参数”后立即持久化，避免用户忘记手动保存。
- 实现方法：
  - 悬浮窗参数编辑（`FloatingWindowService.showPointEditDialog`）保存动作参数后自动调用 `AutoClickCoordinator.saveProfile()`。
  - 首页点击点编辑（`AutoClickScreen.showPointEditDialog`）保存动作参数后自动调用 `AutoClickCoordinator.saveProfile()`。

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
  - 主题：跟随系统深浅模式选择 Miuix 色板，保留灰色半透明视觉风格并提升边框可读性。
  - 对外入口：`start(context)` / `stop(context)` 与 `overlayVisible` 状态流。

## 15. 应用更新与公告检查模块（AppUpdateChecker / AppNoticeChecker + AutoClickScreen 顶部卡片）
- 作用：应用启动时检查线上版本与公告，并在首页顶部提示可点击入口。
- 实现方法：
  - 文件：
    - `app/src/main/java/com/example/littleclicker/update/AppUpdateChecker.kt`
    - `app/src/main/java/com/example/littleclicker/update/AppNoticeChecker.kt`
    - `app/src/main/java/com/example/littleclicker/MainActivity.kt`
    - `app/src/main/java/com/example/littleclicker/ui/AutoClickScreen.kt`
  - 检查地址：
    - 更新：`https://littlecold.cn/littleclicker/version.txt`
    - 公告：`https://littlecold.cn/littleclicker/notice.txt`
  - 解析规则：按 `|` 分割三段（`版本号|下载链接|更新日志`）。
  - 公告规则：按 `|` 分割两段（`链接|内容`）。
  - 比对规则：线上 `versionCode` 大于本地 `BuildConfig.VERSION_CODE` 时判定为有更新。
  - 展示规则：
    - 有公告时显示“公告通知，点击查看”卡片，副文案为公告内容；
    - 有更新时显示“检测到更新！点击下载”卡片，副文案为更新日志。
  - 交互：点击卡片后通过系统浏览器打开下载链接。
