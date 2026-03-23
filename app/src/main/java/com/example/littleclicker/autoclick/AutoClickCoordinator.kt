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

    private const val AUTO_NAME_PREFIX = "点击配置_"

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

    private val _recording = MutableStateFlow(AutoClickRecordingState())
    val recording: StateFlow<AutoClickRecordingState> = _recording.asStateFlow()

    fun initialize(context: Context) {
        val applicationContext = context.applicationContext
        appContext = applicationContext
        if (initialized) {
            refreshProfiles()
            return
        }

        _profile.value = AutoClickRepository.loadActiveProfile(applicationContext)
        refreshProfiles()
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
        val currentProfiles = AutoClickRepository.listProfiles(context)
        val newProfile = base.copy(
            id = "profile_${now}_${(1000..9999).random()}",
            name = name.ifBlank { nextDefaultProfileName(currentProfiles) },
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

    fun deleteProfile(profileId: String): Result<AutoClickProfile> {
        val context = appContext ?: return Result.failure(IllegalStateException("Coordinator not initialized"))

        return runCatching {
            val deleted = AutoClickRepository.deleteProfile(context, profileId)
            if (!deleted) {
                throw IllegalArgumentException("删除失败：配置不存在")
            }

            val remaining = AutoClickRepository.listProfiles(context)
            val activeId = AutoClickRepository.getActiveProfileId(context)
            val isDeletedActive = activeId == null || activeId == profileId || _profile.value.id == profileId

            val nextActive = when {
                remaining.isEmpty() -> {
                    val created = AutoClickProfile(
                        id = "profile_${System.currentTimeMillis()}_${(1000..9999).random()}",
                        name = nextDefaultProfileName(emptyList()),
                        points = emptyList(),
                        cycleCount = 1,
                        startAtMillis = null,
                        updatedAt = System.currentTimeMillis()
                    )
                    AutoClickRepository.saveProfile(context, created, makeActive = true)
                    created
                }

                isDeletedActive -> {
                    val fallback = remaining.first()
                    AutoClickRepository.setActiveProfileId(context, fallback.id)
                    fallback
                }

                else -> {
                    AutoClickRepository.loadProfile(context, _profile.value.id)
                        ?: remaining.first()
                }
            }

            if (isDeletedActive) {
                stop()
                _profile.value = nextActive
                restoreScheduleForCurrentProfile()
            } else {
                _profile.value = nextActive
            }
            refreshProfiles()
            _runtime.value = AutoClickRuntime(
                state = AutoClickRunState.Idle,
                message = "配置已删除，当前配置：${nextActive.name}"
            )
            nextActive
        }
    }

    fun addPoint() {
        addAction(AutoClickActionType.Click)
    }

    fun addAction(actionType: AutoClickActionType): AutoClickPoint {
        var created: AutoClickPoint? = null
        updateProfile { current ->
            val next = createPoint(
                current = current,
                actionType = actionType
            )
            created = next
            current.copy(points = current.points + next)
        }
        return requireNotNull(created) { "新增动作失败：未生成动作点" }
    }

    fun addRecordedTap(x: Int, y: Int): AutoClickPoint? {
        return addRecordedAction(
            actionType = AutoClickActionType.Click,
            startX = x,
            startY = y,
            endX = null,
            endY = null
        )
    }

    fun addRecordedAction(
        actionType: AutoClickActionType,
        startX: Int,
        startY: Int,
        endX: Int? = null,
        endY: Int? = null,
    ): AutoClickPoint? {
        val recordState = _recording.value
        if (!recordState.isRecording) return null
        val now = System.currentTimeMillis()
        val delay = if (recordState.recordedCount == 0) {
            0L
        } else {
            (now - (recordState.lastTapAtMillis ?: now)).coerceAtLeast(0L)
        }

        var created: AutoClickPoint? = null
        updateProfile { current ->
            val nextId = (current.points.maxOfOrNull { it.id } ?: 0) + 1
            val safeStartX = startX.coerceAtLeast(0)
            val safeStartY = startY.coerceAtLeast(0)
            val point = AutoClickPoint(
                id = nextId,
                x = safeStartX,
                y = safeStartY,
                actionType = actionType,
                endX = if (actionType == AutoClickActionType.Swipe) {
                    (endX ?: safeStartX).coerceAtLeast(0)
                } else {
                    null
                },
                endY = if (actionType == AutoClickActionType.Swipe) {
                    (endY ?: safeStartY).coerceAtLeast(0)
                } else {
                    null
                },
                delayMs = delay,
                touchDurationMs = 50L,
                repeatCount = 1
            )
            created = point
            current.copy(points = current.points + point)
        }

        _recording.value = AutoClickRecordingState(
            isRecording = true,
            recordedCount = recordState.recordedCount + 1,
            lastTapAtMillis = now
        )
        return created
    }

    fun startRecording(): Boolean {
        if (_recording.value.isRecording) return false
        if (_runtime.value.state == AutoClickRunState.Running || _runtime.value.state == AutoClickRunState.Paused) {
            _runtime.value = AutoClickRuntime(
                state = AutoClickRunState.Failed,
                message = "运行中不可录制，请先停止"
            )
            return false
        }
        _recording.value = AutoClickRecordingState(isRecording = true)
        _runtime.value = AutoClickRuntime(
            state = AutoClickRunState.Idle,
            message = "录制中：点击屏幕可添加动作"
        )
        return true
    }

    fun stopRecording(): Boolean {
        if (!_recording.value.isRecording) return false
        val count = _recording.value.recordedCount
        _recording.value = AutoClickRecordingState(isRecording = false)
        _runtime.value = AutoClickRuntime(
            state = AutoClickRunState.Idle,
            message = "录制结束，共记录 $count 个动作"
        )
        return true
    }

    fun removeLatestPoint(): AutoClickPoint? {
        var removed: AutoClickPoint? = null
        updateProfile { current ->
            val latest = current.points.lastOrNull()
            removed = latest
            if (latest == null) {
                current
            } else {
                current.copy(points = current.points.dropLast(1))
            }
        }
        return removed
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
                        val safeX = x.coerceAtLeast(0)
                        val safeY = y.coerceAtLeast(0)
                        val dx = safeX - point.x
                        val dy = safeY - point.y
                        val movedEndX = point.endX?.plus(dx)?.coerceAtLeast(0)
                        val movedEndY = point.endY?.plus(dy)?.coerceAtLeast(0)
                        point.copy(x = safeX, y = safeY, endX = movedEndX, endY = movedEndY)
                    }
                }
            )
        }
    }

    fun updatePointConfig(
        pointId: Int,
        x: Int? = null,
        y: Int? = null,
        actionType: AutoClickActionType? = null,
        endX: Int? = null,
        endY: Int? = null,
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
                        val resolvedType = actionType ?: point.actionType
                        val resolvedX = x?.coerceAtLeast(0) ?: point.x
                        val resolvedY = y?.coerceAtLeast(0) ?: point.y
                        val resolvedEndX = when (resolvedType) {
                            AutoClickActionType.Click -> null
                            AutoClickActionType.Swipe -> (endX ?: point.endX ?: (resolvedX + 200)).coerceAtLeast(0)
                        }
                        val resolvedEndY = when (resolvedType) {
                            AutoClickActionType.Click -> null
                            AutoClickActionType.Swipe -> (endY ?: point.endY ?: resolvedY).coerceAtLeast(0)
                        }
                        point.copy(
                            x = resolvedX,
                            y = resolvedY,
                            actionType = resolvedType,
                            endX = resolvedEndX,
                            endY = resolvedEndY,
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

    fun updateRunMode(runMode: AutoClickRunMode) {
        updateProfile { current ->
            current.copy(runMode = runMode)
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

        if (_recording.value.isRecording) {
            _runtime.value = AutoClickRuntime(
                state = AutoClickRunState.Failed,
                message = "录制中，无法开始运行"
            )
            return false
        }

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
        if (_recording.value.isRecording) {
            _recording.value = AutoClickRecordingState(isRecording = false)
        }
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
        val profiles = AutoClickRepository.listProfiles(context)
        val normalized = if (snapshot.name.isBlank()) {
            snapshot.copy(name = nextAutoProfileName(profiles))
        } else {
            snapshot
        }
        return runCatching {
            AutoClickRepository.saveProfile(context, normalized, makeActive = true)
            _profile.value = normalized
            refreshProfiles()
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

    private fun nextDefaultProfileName(profiles: List<AutoClickProfile>): String {
        return nextAutoProfileName(profiles)
    }

    private fun nextAutoProfileName(profiles: List<AutoClickProfile>): String {
        return "$AUTO_NAME_PREFIX${profiles.size + 1}"
    }

    private fun updateProfile(transform: (AutoClickProfile) -> AutoClickProfile) {
        _profile.update { current ->
            val updated = transform(current)
            updated.copy(
                cycleCount = updated.cycleCount.coerceAtLeast(1),
                points = updated.points.map { point ->
                    val normalizedType = point.actionType
                    point.copy(
                        actionType = normalizedType,
                        endX = when (normalizedType) {
                            AutoClickActionType.Click -> null
                            AutoClickActionType.Swipe -> (point.endX ?: (point.x + 200)).coerceAtLeast(0)
                        },
                        endY = when (normalizedType) {
                            AutoClickActionType.Click -> null
                            AutoClickActionType.Swipe -> (point.endY ?: point.y).coerceAtLeast(0)
                        },
                        delayMs = point.delayMs.coerceAtLeast(0L),
                        touchDurationMs = point.touchDurationMs.coerceAtLeast(1L),
                        repeatCount = point.repeatCount.coerceAtLeast(1)
                    )
                },
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    private fun createPoint(
        current: AutoClickProfile,
        actionType: AutoClickActionType,
    ): AutoClickPoint {
        val nextId = (current.points.maxOfOrNull { it.id } ?: 0) + 1
        val base = 240 + current.points.size * 120
        val boundedBase = base.coerceAtLeast(0)
        return when (actionType) {
            AutoClickActionType.Click -> AutoClickPoint(
                id = nextId,
                x = boundedBase,
                y = boundedBase,
                actionType = AutoClickActionType.Click
            )

            AutoClickActionType.Swipe -> AutoClickPoint(
                id = nextId,
                x = boundedBase,
                y = boundedBase,
                actionType = AutoClickActionType.Swipe,
                endX = boundedBase + 200,
                endY = boundedBase
            )
        }
    }
}
