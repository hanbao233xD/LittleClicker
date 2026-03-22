# AGENTS.md

## Startup Context Rule
在每次新对话开始时，必须按以下顺序加载上下文：

1. 优先阅读项目根目录的 `AI_MEMORY.md`。
2. 如果 `AI_MEMORY.md` 不存在，再阅读 `docs/_index.md`（若存在）。
3. 再根据用户问题按需读取 `docs/` 下相关原文文件。
4. 不要默认全量读取 `docs/` 所有文件，除非用户明确要求“全量阅读”。
5.如果未特殊说明，所有ui使用miuix开发
## Missing File Handling
如果 `AI_MEMORY.md` 与 `docs/_index.md` 都不存在：

1. 先明确告知用户当前缺少启动上下文文件。
2. 建议用户创建 `AI_MEMORY.md`，并给出可维护的摘要结构（项目目标、模块概览、关键约束、最近变更）。
