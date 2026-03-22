# LittleClicker 代码经验沉淀

最后更新：2026-03-22

## 使用规则
1. 本文件专门记录“编码过程中的经验、踩坑与最佳实践”。
2. 每次完成重要开发后都追加一条，避免重复踩坑。

## 经验记录
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
