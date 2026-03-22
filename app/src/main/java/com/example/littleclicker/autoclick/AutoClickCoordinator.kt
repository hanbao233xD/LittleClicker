package com.example.littleclicker.autoclick

import android.content.Context
import com.example.littleclicker.service.AutoClickAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object AutoClickCoordinator {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var appContext: Context? = null
    private var initialized = false
    private var scheduleJob: Job? = null

    private val _profile = MutableStateFlow(AutoClickProfile())
    val profile: StateFlow<AutoClickProfile> = _profile.asStateFlow()

    private val _profiles = MutableStateFlow<List<AutoClickProfile>>(emptyList())
    val profiles: StateFlow<List<AutoClickProfile>> = _profiles.asStateFlow()

    private val _runtime = MutableStateFlow(AutoClickRuntime())
    val runtime: StateFlow<AutoClickRuntime> = _runtime.asStateFlow()

    private val _scriptDrafts = MutableStateFlow<List<ScriptDraft>>(emptyList())
    val scriptDrafts: StateFlow<List<ScriptDraft>> = _scriptDrafts.asStateFlow()

    fun initialize(context: Context) {
        val applicationContext = context.applicationContext
        appContext = applicationContext
        if (initialized) {
            refreshProfiles()
            return
        }

        _profile.value = AutoClickRepository.loadActiveProfile(applicationContext)
        refreshProfiles()
        refreshScriptDrafts()
        initialized = true
        restoreScheduleForCurrentProfile()
    }

    fun refreshProfiles() {
        val context = appContext ?: return
        _profiles.value = AutoClickRepository.listProfiles(context)
    }

    fun loadProfile(profileId: String): Result<AutoClickProfile> {
        val context = appContext ?: return Result.failure(IllegalStateException("Coordinator not initialized"))

        return runCatching {
            stop()
            val loaded = AutoClickRepository.loadProfile(context, profileId)
                ?: throw IllegalArgumentException("找不到指定配置")
            AutoClickRepository.setActiveProfileId(context, loaded.id)
            _profile.value = loaded
            refreshProfiles()
            restoreScheduleForCurrentProfile()
            loaded
        }
    }

    fun saveAsNewProfile(name: String): Result<AutoClickProfile> {
        val context = appContext ?: return Result.failure(IllegalStateException("Coordinator not initialized"))
        val base = _profile.value
        val now = System.currentTimeMillis()
        val newProfile = base.copy(
            id = "profile_${now}_${(1000..9999).random()}",
            name = name.ifBlank { "配置_$now" },
            startAtMillis = null,
            updatedAt = now
        )

        return runCatching {
            AutoClickRepository.saveProfile(context, newProfile, makeActive = true)
            _profile.value = newProfile
            refreshProfiles()
            _runtime.value = AutoClickRuntime(
                state = AutoClickRunState.Idle,
                message = "已切换到新配置：${newProfile.name}"
            )
            newProfile
        }
    }

    fun addPoint() {
        updateProfile { current ->
            val nextId = (current.points.maxOfOrNull { it.id } ?: 0) + 1
            val base = 240 + current.points.size * 120
            current.copy(points = current.points + AutoClickPoint(id = nextId, x = base, y = base))
        }
    }

    fun removePoint(pointId: Int) {
        updateProfile { current ->
            current.copy(points = current.points.filterNot { it.id == pointId })
        }
    }

    fun movePointBy(pointId: Int, dx: Int, dy: Int) {
        if (dx == 0 && dy == 0) return
        updateProfile { current ->
            current.copy(
                points = current.points.map { point ->
                    if (point.id != pointId) {
                        point
                    } else {
                        point.copy(
                            x = (point.x + dx).coerceAtLeast(0),
                            y = (point.y + dy).coerceAtLeast(0)
                        )
                    }
                }
            )
        }
    }

    fun setPointPosition(pointId: Int, x: Int, y: Int) {
        updateProfile { current ->
            current.copy(
                points = current.points.map { point ->
                    if (point.id != pointId) {
                        point
                    } else {
                        point.copy(
                            x = x.coerceAtLeast(0),
                            y = y.coerceAtLeast(0)
                        )
                    }
                }
            )
        }
    }

    fun updatePointConfig(
        pointId: Int,
        x: Int? = null,
        y: Int? = null,
        delayMs: Long? = null,
        touchDurationMs: Long? = null,
        repeatCount: Int? = null,
    ) {
        updateProfile { current ->
            current.copy(
                points = current.points.map { point ->
                    if (point.id != pointId) {
                        point
                    } else {
                        point.copy(
                            x = x?.coerceAtLeast(0) ?: point.x,
                            y = y?.coerceAtLeast(0) ?: point.y,
                            delayMs = delayMs?.coerceAtLeast(0L) ?: point.delayMs,
                            touchDurationMs = touchDurationMs?.coerceAtLeast(1L) ?: point.touchDurationMs,
                            repeatCount = repeatCount?.coerceAtLeast(1) ?: point.repeatCount
                        )
                    }
                }
            )
        }
    }

    fun updateProfileName(name: String) {
        updateProfile { current ->
            current.copy(name = name)
        }
    }

    fun updateCycleCount(cycleCount: Int) {
        updateProfile { current ->
            current.copy(cycleCount = cycleCount.coerceAtLeast(1))
        }
    }

    fun clearScheduleTime() {
        cancelSchedule()
        updateProfile { current -> current.copy(startAtMillis = null) }
        _runtime.value = AutoClickRuntime(
            state = AutoClickRunState.Idle,
            message = "已清除定时"
        )
    }

    fun scheduleAt(startAtMillis: Long): Boolean {
        if (startAtMillis <= System.currentTimeMillis()) {
            _runtime.value = AutoClickRuntime(
                state = AutoClickRunState.Failed,
                message = "定时时间已过期，请重新设置",
                scheduledAtMillis = startAtMillis
            )
            return false
        }

        updateProfile { current -> current.copy(startAtMillis = startAtMillis) }
        cancelSchedule()
        _runtime.value = AutoClickRuntime(
            state = AutoClickRunState.Scheduled,
            message = "已定时，等待开始",
            scheduledAtMillis = startAtMillis
        )

        scheduleJob = scope.launch {
            val delayMillis = (startAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            delay(delayMillis)
            startNow(fromSchedule = true)
        }
        return true
    }

    fun startNow(fromSchedule: Boolean = false): Boolean {
        cancelSchedule()

        val profileSnapshot = _profile.value
        if (profileSnapshot.points.isEmpty()) {
            _runtime.value = AutoClickRuntime(
                state = AutoClickRunState.Failed,
                message = "请先添加至少一个点击点"
            )
            return false
        }

        val started = AutoClickAccessibilityService.start(profileSnapshot)
        _runtime.value = if (started) {
            AutoClickRuntime(
                state = AutoClickRunState.Running,
                message = if (fromSchedule) "定时触发，正在执行" else "正在执行自动点击"
            )
        } else {
            AutoClickRuntime(
                state = AutoClickRunState.Failed,
                message = "无障碍服务不可用或已有任务在运行"
            )
        }

        if (started) {
            updateProfile { current -> current.copy(startAtMillis = null) }
        }
        return started
    }

    fun pause(): Boolean {
        val paused = AutoClickAccessibilityService.pause()
        if (paused) {
            _runtime.value = AutoClickRuntime(
                state = AutoClickRunState.Paused,
                message = "自动点击已暂停"
            )
        }
        return paused
    }

    fun resume(): Boolean {
        val resumed = AutoClickAccessibilityService.resume()
        if (resumed) {
            _runtime.value = AutoClickRuntime(
                state = AutoClickRunState.Running,
                message = "自动点击继续执行"
            )
        }
        return resumed
    }

    fun stop(): Boolean {
        cancelSchedule()
        val stopped = AutoClickAccessibilityService.stop()
        if (stopped) {
            _runtime.value = AutoClickRuntime(
                state = AutoClickRunState.Idle,
                message = "自动点击已停止"
            )
        }
        return stopped
    }

    fun saveProfile(): Result<Unit> {
        val context = appContext ?: return Result.failure(IllegalStateException("Coordinator not initialized"))
        val snapshot = _profile.value
        return runCatching {
            AutoClickRepository.saveProfile(context, snapshot, makeActive = true)
            refreshProfiles()
        }
    }

    fun refreshScriptDrafts() {
        val context = appContext ?: return
        _scriptDrafts.value = AutoClickRepository.listDrafts(context)
    }

    fun createScriptDraft(name: String): Result<ScriptDraft> {
        val context = appContext ?: return Result.failure(IllegalStateException("Coordinator not initialized"))
        if (name.isBlank()) {
            return Result.failure(IllegalArgumentException("脚本名称不能为空"))
        }
        return runCatching {
            AutoClickRepository.createDraft(context, name).also { refreshScriptDrafts() }
        }
    }

    fun saveScriptDraft(draftId: String, name: String): Result<ScriptDraft> {
        val context = appContext ?: return Result.failure(IllegalStateException("Coordinator not initialized"))
        if (name.isBlank()) {
            return Result.failure(IllegalArgumentException("脚本名称不能为空"))
        }

        return runCatching {
            val existing = AutoClickRepository.loadDraft(context, draftId)
                ?: throw IllegalArgumentException("找不到指定脚本草稿")
            val updated = existing.copy(name = name, updatedAt = System.currentTimeMillis())
            AutoClickRepository.saveDraft(context, updated)
            refreshScriptDrafts()
            updated
        }
    }

    fun reportExecutionState(state: AutoClickRunState, message: String? = null) {
        val runtime = AutoClickRuntime(
            state = state,
            message = message,
            scheduledAtMillis = if (state == AutoClickRunState.Scheduled) _profile.value.startAtMillis else null
        )
        _runtime.value = runtime
    }

    private fun restoreScheduleForCurrentProfile() {
        val pendingStartAt = _profile.value.startAtMillis
        if (pendingStartAt == null) {
            _runtime.value = AutoClickRuntime(state = AutoClickRunState.Idle, message = "配置已加载")
            return
        }

        if (pendingStartAt > System.currentTimeMillis()) {
            scheduleAt(pendingStartAt)
        } else {
            _runtime.value = AutoClickRuntime(
                state = AutoClickRunState.Failed,
                message = "当前配置中的定时已过期，请重新设置",
                scheduledAtMillis = pendingStartAt
            )
        }
    }

    private fun cancelSchedule() {
        scheduleJob?.cancel()
        scheduleJob = null
    }

    private fun updateProfile(transform: (AutoClickProfile) -> AutoClickProfile) {
        _profile.update { current ->
            val updated = transform(current)
            updated.copy(
                cycleCount = updated.cycleCount.coerceAtLeast(1),
                points = updated.points.map { point ->
                    point.copy(
                        delayMs = point.delayMs.coerceAtLeast(0L),
                        touchDurationMs = point.touchDurationMs.coerceAtLeast(1L),
                        repeatCount = point.repeatCount.coerceAtLeast(1)
                    )
                },
                updatedAt = System.currentTimeMillis()
            )
        }
    }
}
