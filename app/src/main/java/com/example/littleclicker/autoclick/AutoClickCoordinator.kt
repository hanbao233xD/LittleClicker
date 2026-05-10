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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

data class ScheduleAtHmsResult(
    val scheduledAtMillis: Long,
    val rolledToNextDay: Boolean,
)

object AutoClickCoordinator {

    private const val AUTO_NAME_PREFIX = "点击配置_"
    private const val SCHEDULE_POLL_INTERVAL_MS = 30L
    private const val AUTO_SAVE_INTERVAL_MS = 1_000L
    private const val FIRST_RECORDED_ACTION_DELAY_MS = 100L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val saveLock = Any()

    private var appContext: Context? = null
    private var initialized = false
    private var scheduleJob: Job? = null
    private var timeSyncJob: Job? = null
    private var autoSaveJob: Job? = null
    @Volatile
    private var overlayCoordinateOffsetX: Int = 0
    @Volatile
    private var overlayCoordinateOffsetY: Int = 0

    private val _profile = MutableStateFlow(AutoClickProfile())
    val profile: StateFlow<AutoClickProfile> = _profile.asStateFlow()

    private val _profiles = MutableStateFlow<List<AutoClickProfile>>(emptyList())
    val profiles: StateFlow<List<AutoClickProfile>> = _profiles.asStateFlow()

    private val _runtime = MutableStateFlow(AutoClickRuntime())
    val runtime: StateFlow<AutoClickRuntime> = _runtime.asStateFlow()

    private val _recording = MutableStateFlow(AutoClickRecordingState())
    val recording: StateFlow<AutoClickRecordingState> = _recording.asStateFlow()

    private val _timeSync = MutableStateFlow(TimeSyncState())
    val timeSync: StateFlow<TimeSyncState> = _timeSync.asStateFlow()

    fun initialize(context: Context) {
        val applicationContext = context.applicationContext
        appContext = applicationContext
        if (initialized) {
            refreshProfiles()
            refreshTimeSyncServerFromProfile()
            startAutoSaveLoopIfNeeded()
            return
        }

        _profile.value = AutoClickRepository.loadActiveProfile(applicationContext)
        refreshProfiles()
        initialized = true
        refreshTimeSyncServerFromProfile()
        syncNtpTime(force = false)
        restoreScheduleForCurrentProfile()
        startAutoSaveLoopIfNeeded()
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
            refreshTimeSyncServerFromProfile()
            syncNtpTime(force = false)
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
            id = generateProfileId(now),
            name = name.ifBlank { nextDefaultProfileName(currentProfiles) },
            startAtMillis = null,
            updatedAt = now
        )

        return runCatching {
            persistProfileToStorage(context, newProfile, makeActive = true)
            _profile.value = newProfile
            refreshProfiles()
            refreshTimeSyncServerFromProfile()
            _runtime.value = AutoClickRuntime(
                state = AutoClickRunState.Idle,
                message = "已切换到新配置：${newProfile.name}"
            )
            newProfile
        }
    }

    fun saveAsEmptyProfile(name: String): Result<AutoClickProfile> {
        val context = appContext ?: return Result.failure(IllegalStateException("Coordinator not initialized"))
        val base = _profile.value
        val now = System.currentTimeMillis()
        val currentProfiles = AutoClickRepository.listProfiles(context)
        val newProfile = base.copy(
            id = generateProfileId(now),
            name = name.ifBlank { nextDefaultProfileName(currentProfiles) },
            points = emptyList(),
            startAtMillis = null,
            updatedAt = now
        )

        return runCatching {
            persistProfileToStorage(context, newProfile, makeActive = true)
            _profile.value = newProfile
            refreshProfiles()
            refreshTimeSyncServerFromProfile()
            _runtime.value = AutoClickRuntime(
                state = AutoClickRunState.Idle,
                message = "已保存为空配置：${newProfile.name}"
            )
            newProfile
        }
    }

    fun importProfile(json: String): Result<AutoClickProfile> {
        val context = appContext ?: return Result.failure(IllegalStateException("Coordinator not initialized"))
        return runCatching {
            val parsed = AutoClickRepository.profileFromJson(json)
            val now = System.currentTimeMillis()
            val currentProfiles = AutoClickRepository.listProfiles(context)
            val existingNames = currentProfiles.map { it.name }.toSet()
            val finalName = "${parsed.name}_导入"
            val newProfile = parsed.copy(
                id = generateProfileId(now),
                name = finalName,
                startAtMillis = null,
                updatedAt = now
            )
            persistProfileToStorage(context, newProfile, makeActive = true)
            _profile.value = newProfile
            refreshProfiles()
            refreshTimeSyncServerFromProfile()
            _runtime.value = AutoClickRuntime(
                state = AutoClickRunState.Idle,
                message = "已导入配置：${newProfile.name}"
            )
            newProfile
        }
    }

    fun deleteProfile(profileId: String): Result<AutoClickProfile> {
        val context = appContext ?: return Result.failure(IllegalStateException("Coordinator not initialized"))

        return runCatching {
            val deleted = deleteProfileFromStorage(context, profileId)
            if (!deleted) {
                throw IllegalArgumentException("删除失败：配置不存在")
            }

            val remaining = AutoClickRepository.listProfiles(context)
            val activeId = AutoClickRepository.getActiveProfileId(context)
            val isDeletedActive = activeId == null || activeId == profileId || _profile.value.id == profileId

            val nextActive = when {
                remaining.isEmpty() -> {
                    val now = System.currentTimeMillis()
                    val created = AutoClickProfile(
                        id = generateProfileId(now),
                        name = nextDefaultProfileName(emptyList()),
                        points = emptyList(),
                        cycleCount = 1,
                        startAtMillis = null,
                        updatedAt = now
                    )
                    persistProfileToStorage(context, created, makeActive = true)
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
                refreshTimeSyncServerFromProfile()
                syncNtpTime(force = false)
                restoreScheduleForCurrentProfile()
            } else {
                _profile.value = nextActive
                refreshTimeSyncServerFromProfile()
            }
            refreshProfiles()
            _runtime.value = AutoClickRuntime(
                state = AutoClickRunState.Idle,
                message = "配置已删除，当前配置：${nextActive.name}"
            )
            nextActive
        }
    }

    fun duplicateProfile(profileId: String): Result<AutoClickProfile> {
        val context = appContext ?: return Result.failure(IllegalStateException("Coordinator not initialized"))

        return runCatching {
            val source = AutoClickRepository.loadProfile(context, profileId)
                ?: throw IllegalArgumentException("复制失败：找不到指定配置")
            val now = System.currentTimeMillis()
            val duplicated = source.copy(
                id = generateProfileId(now),
                name = "${source.name}_副本",
                updatedAt = now
            )
            persistProfileToStorage(context, duplicated, makeActive = false)
            refreshProfiles()
            duplicated
        }
    }

    fun renameProfile(profileId: String, newName: String): Result<AutoClickProfile> {
        val context = appContext ?: return Result.failure(IllegalStateException("Coordinator not initialized"))
        val normalizedName = newName.trim()
        if (normalizedName.isBlank()) {
            return Result.failure(IllegalArgumentException("配置名称不能为空"))
        }

        return runCatching {
            val stored = AutoClickRepository.loadProfile(context, profileId)
                ?: throw IllegalArgumentException("重命名失败：找不到指定配置")
            val activeId = AutoClickRepository.getActiveProfileId(context)
            val isActive = activeId == profileId || _profile.value.id == profileId
            val base = if (_profile.value.id == profileId) _profile.value else stored
            val renamed = base.copy(
                name = normalizedName,
                updatedAt = System.currentTimeMillis()
            )
            persistProfileToStorage(context, renamed, makeActive = isActive)
            if (isActive) {
                _profile.value = renamed
            }
            refreshProfiles()
            renamed
        }
    }

    fun discardUnsavedChanges(): Result<AutoClickProfile> {
        val context = appContext ?: return Result.failure(IllegalStateException("Coordinator not initialized"))

        return runCatching {
            val activeId = AutoClickRepository.getActiveProfileId(context)
            val restored = if (!activeId.isNullOrBlank()) {
                AutoClickRepository.loadProfile(context, activeId)
            } else {
                null
            } ?: AutoClickRepository.loadActiveProfile(context)
            _profile.value = restored
            refreshProfiles()
            refreshTimeSyncServerFromProfile()
            restoreScheduleForCurrentProfile()
            restored
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
        touchDurationMs: Long? = null,
        actionStartAtMillis: Long? = null,
    ): AutoClickPoint? {
        val recordState = _recording.value
        if (!recordState.isRecording) return null
        val now = System.currentTimeMillis()
        val actionStartedAt = actionStartAtMillis ?: now
        val delay = if (recordState.recordedCount == 0) {
            FIRST_RECORDED_ACTION_DELAY_MS
        } else {
            (actionStartedAt - (recordState.lastTapAtMillis ?: actionStartedAt)).coerceAtLeast(0L)
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
                touchDurationMs = (touchDurationMs ?: 50L).coerceAtLeast(1L),
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
        if (_runtime.value.state == AutoClickRunState.Running ||
            _runtime.value.state == AutoClickRunState.Paused ||
            AutoClickAccessibilityService.isExecuting()
        ) {
            _runtime.value = AutoClickRuntime(
                state = AutoClickRunState.Failed,
                message = "执行中不可录制，请先停止"
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
        targetText: String? = null,
        textFindRetryCount: Int? = null,
        textFindRetryDelayMs: Long? = null,
        continuousRetry: Boolean? = null,
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
                            AutoClickActionType.Home -> null
                            AutoClickActionType.Back -> null
                            AutoClickActionType.Recents -> null
                            AutoClickActionType.TextClick -> null
                        }
                        val resolvedEndY = when (resolvedType) {
                            AutoClickActionType.Click -> null
                            AutoClickActionType.Swipe -> (endY ?: point.endY ?: resolvedY).coerceAtLeast(0)
                            AutoClickActionType.Home -> null
                            AutoClickActionType.Back -> null
                            AutoClickActionType.Recents -> null
                            AutoClickActionType.TextClick -> null
                        }
                        point.copy(
                            x = resolvedX,
                            y = resolvedY,
                            actionType = resolvedType,
                            endX = resolvedEndX,
                            endY = resolvedEndY,
                            delayMs = delayMs?.coerceAtLeast(0L) ?: point.delayMs,
                            touchDurationMs = touchDurationMs?.coerceAtLeast(1L) ?: point.touchDurationMs,
                            repeatCount = repeatCount?.coerceAtLeast(1) ?: point.repeatCount,
                            targetText = targetText ?: point.targetText,
                            textFindRetryCount = textFindRetryCount?.coerceAtLeast(0) ?: point.textFindRetryCount,
                            textFindRetryDelayMs = textFindRetryDelayMs?.coerceAtLeast(0L) ?: point.textFindRetryDelayMs,
                            continuousRetry = continuousRetry ?: point.continuousRetry
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

    fun updateLayoutLocked(layoutLocked: Boolean) {
        updateProfile { current ->
            current.copy(layoutLocked = layoutLocked)
        }
    }

    fun updatePanelCollapsed(collapsed: Boolean) {
        updateProfile { current ->
            current.copy(panelCollapsed = collapsed)
        }
    }

    fun updateRunMode(runMode: AutoClickRunMode) {
        updateProfile { current ->
            current.copy(runMode = runMode)
        }
    }

    fun updateLoopIntervalDelay(loopIntervalDelayMs: Long) {
        updateProfile { current ->
            current.copy(loopIntervalDelayMs = loopIntervalDelayMs.coerceAtLeast(0L))
        }
    }

    fun updateClickRandomOffsetPx(clickRandomOffsetPx: Int) {
        updateProfile { current ->
            current.copy(clickRandomOffsetPx = clickRandomOffsetPx.coerceAtLeast(0))
        }
    }

    fun updateRandomDelayMs(randomDelayMs: Long) {
        updateProfile { current ->
            current.copy(randomDelayMs = randomDelayMs.coerceAtLeast(0L))
        }
    }

    fun updateRecordingMode(recordingMode: AutoClickRecordingMode) {
        updateProfile { current ->
            current.copy(recordingMode = recordingMode)
        }
    }

    fun updateNtpServer(host: String) {
        val normalized = host.trim().ifBlank { DEFAULT_NTP_SERVER_HOST }
        updateProfile { current -> current.copy(ntpServerHost = normalized) }
        _timeSync.update {
            it.copy(
                serverHost = normalized,
                isSynced = false,
                delayMillis = null,
                fallbackToDeviceTime = true,
                errorMessage = "正在校时..."
            )
        }
        syncNtpTime(force = true)
    }

    fun syncNtpTime(force: Boolean = true) {
        val targetServer = _profile.value.ntpServerHost.ifBlank { DEFAULT_NTP_SERVER_HOST }
        if (!force && _timeSync.value.serverHost == targetServer && _timeSync.value.isSynced) {
            return
        }
        if (timeSyncJob?.isActive == true) {
            if (!force) return
            timeSyncJob?.cancel()
        }

        _timeSync.update {
            it.copy(
                serverHost = targetServer,
                errorMessage = if (force) "正在校时..." else it.errorMessage
            )
        }

        timeSyncJob = scope.launch {
            runCatching {
                SntpClient.query(targetServer)
            }.onSuccess { result ->
                _timeSync.value = TimeSyncState(
                    serverHost = result.serverHost,
                    isSynced = true,
                    offsetMillis = result.offsetMillis,
                    delayMillis = result.delayMillis,
                    fallbackToDeviceTime = false,
                    errorMessage = null
                )
            }.onFailure { error ->
                _timeSync.value = TimeSyncState(
                    serverHost = targetServer,
                    isSynced = false,
                    offsetMillis = 0L,
                    delayMillis = null,
                    fallbackToDeviceTime = true,
                    errorMessage = error.message ?: "NTP 校时失败"
                )
            }
        }
    }

    fun currentAlignedNowMillis(): Long {
        val sync = _timeSync.value
        return System.currentTimeMillis() + sync.offsetMillis
    }

    fun clearScheduleTime() {
        cancelSchedule()
        updateProfile { current -> current.copy(startAtMillis = null, scheduleRuleHms = null) }
        _runtime.value = AutoClickRuntime(
            state = AutoClickRunState.Idle,
            message = "已清除定时"
        )
    }

    fun scheduleAtHms(hour: Int, minute: Int, second: Int): ScheduleAtHmsResult {
        syncNtpTime(force = true)

        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        val safeSecond = second.coerceIn(0, 59)
        val rule = formatHms(safeHour, safeMinute, safeSecond)
        val now = currentAlignedNowMillis()
        val targetCalendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, safeHour)
            set(Calendar.MINUTE, safeMinute)
            set(Calendar.SECOND, safeSecond)
            set(Calendar.MILLISECOND, 0)
        }
        val rolledToNextDay = if (targetCalendar.timeInMillis <= now) {
            targetCalendar.add(Calendar.DAY_OF_YEAR, 1)
            true
        } else {
            false
        }
        val target = targetCalendar.timeInMillis

        updateProfile { current ->
            current.copy(
                scheduleRuleHms = rule,
                startAtMillis = target
            )
        }
        armSchedule(target)
        return ScheduleAtHmsResult(
            scheduledAtMillis = target,
            rolledToNextDay = rolledToNextDay
        )
    }

    fun scheduleAt(startAtMillis: Long): Boolean {
        if (startAtMillis <= currentAlignedNowMillis()) {
            _runtime.value = AutoClickRuntime(
                state = AutoClickRunState.Failed,
                message = "定时时间已过期，请重新选择",
                scheduledAtMillis = startAtMillis
            )
            return false
        }

        val calendar = Calendar.getInstance().apply { timeInMillis = startAtMillis }
        val rule = formatHms(
            hour = calendar.get(Calendar.HOUR_OF_DAY),
            minute = calendar.get(Calendar.MINUTE),
            second = calendar.get(Calendar.SECOND)
        )
        updateProfile { current -> current.copy(startAtMillis = startAtMillis, scheduleRuleHms = rule) }
        armSchedule(startAtMillis)
        return true
    }

    fun startNow(fromSchedule: Boolean = false): Boolean {
        cancelSchedule()

        if (_recording.value.isRecording) {
            _runtime.value = AutoClickRuntime(
                state = AutoClickRunState.Failed,
                message = "录制中，禁止执行脚本"
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
            val runModeLabel = when (profileSnapshot.runMode) {
                AutoClickRunMode.RunOnce -> "运行一次"
                AutoClickRunMode.LoopUntilStopped -> "循环运行"
            }
            AutoClickRuntime(
                state = AutoClickRunState.Running,
                message = if (fromSchedule) {
                    "定时触发，按运行方式：$runModeLabel"
                } else {
                    "正在执行自动点击（$runModeLabel）"
                }
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
            persistProfileToStorage(context, normalized, makeActive = true)
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

    fun updateOverlayCoordinateOffset(offsetX: Int, offsetY: Int) {
        overlayCoordinateOffsetX = offsetX
        overlayCoordinateOffsetY = offsetY
    }

    fun toScreenCoordinateX(windowX: Int): Int {
        return (windowX + overlayCoordinateOffsetX).coerceAtLeast(0)
    }

    fun toScreenCoordinateY(windowY: Int): Int {
        return (windowY + overlayCoordinateOffsetY).coerceAtLeast(0)
    }

    private fun restoreScheduleForCurrentProfile() {
        val pendingStartAt = _profile.value.startAtMillis
        if (pendingStartAt == null) {
            if (_runtime.value.state == AutoClickRunState.Scheduled) {
                _runtime.value = AutoClickRuntime(state = AutoClickRunState.Idle, message = "配置已加载")
            }
            return
        }

        if (pendingStartAt > currentAlignedNowMillis()) {
            armSchedule(pendingStartAt)
        } else {
            _runtime.value = AutoClickRuntime(
                state = AutoClickRunState.Failed,
                message = "当前配置中的定时已过期，请重新设置",
                scheduledAtMillis = pendingStartAt
            )
        }
    }

    private fun armSchedule(startAtMillis: Long) {
        cancelSchedule()
        _runtime.value = AutoClickRuntime(
            state = AutoClickRunState.Scheduled,
            message = "已定时，等待开始",
            scheduledAtMillis = startAtMillis
        )

        scheduleJob = scope.launch {
            while (isActive) {
                val delta = startAtMillis - currentAlignedNowMillis()
                if (delta <= 0L) {
                    startNow(fromSchedule = true)
                    break
                }
                delay(minOf(SCHEDULE_POLL_INTERVAL_MS, delta))
            }
        }
    }

    private fun refreshTimeSyncServerFromProfile() {
        val server = _profile.value.ntpServerHost.ifBlank { DEFAULT_NTP_SERVER_HOST }
        _timeSync.update {
            if (it.serverHost == server) it else it.copy(serverHost = server)
        }
    }

    private fun cancelSchedule() {
        scheduleJob?.cancel()
        scheduleJob = null
    }

    private fun startAutoSaveLoopIfNeeded() {
        if (autoSaveJob?.isActive == true) return
        autoSaveJob = scope.launch {
            while (isActive) {
                delay(AUTO_SAVE_INTERVAL_MS)
                autoSaveCurrentProfile()
            }
        }
    }

    private fun autoSaveCurrentProfile() {
        val context = appContext ?: return
        runCatching {
            persistProfileToStorage(context, _profile.value, makeActive = true)
        }
    }

    private fun persistProfileToStorage(
        context: Context,
        profile: AutoClickProfile,
        makeActive: Boolean,
    ) {
        synchronized(saveLock) {
            AutoClickRepository.saveProfile(context, profile, makeActive)
        }
    }

    private fun deleteProfileFromStorage(context: Context, profileId: String): Boolean {
        return synchronized(saveLock) {
            AutoClickRepository.deleteProfile(context, profileId)
        }
    }

    private fun nextDefaultProfileName(profiles: List<AutoClickProfile>): String {
        return nextAutoProfileName(profiles)
    }

    private fun nextAutoProfileName(profiles: List<AutoClickProfile>): String {
        return "$AUTO_NAME_PREFIX${profiles.size + 1}"
    }

    private fun generateProfileId(timestampMillis: Long): String {
        return "profile_${timestampMillis}_${(1000..9999).random()}"
    }

    private fun updateProfile(transform: (AutoClickProfile) -> AutoClickProfile) {
        _profile.update { current ->
            val updated = transform(current)
            updated.copy(
                cycleCount = updated.cycleCount.coerceAtLeast(1),
                loopIntervalDelayMs = updated.loopIntervalDelayMs.coerceAtLeast(0L),
                clickRandomOffsetPx = updated.clickRandomOffsetPx.coerceAtLeast(0),
                randomDelayMs = updated.randomDelayMs.coerceAtLeast(0L),
                ntpServerHost = updated.ntpServerHost.ifBlank { DEFAULT_NTP_SERVER_HOST },
                scheduleRuleHms = normalizeScheduleRuleHms(updated.scheduleRuleHms),
                points = updated.points.map { point ->
                    val normalizedType = point.actionType
                    point.copy(
                        actionType = normalizedType,
                        endX = when (normalizedType) {
                            AutoClickActionType.Click -> null
                            AutoClickActionType.Swipe -> (point.endX ?: (point.x + 200)).coerceAtLeast(0)
                            AutoClickActionType.Home -> null
                            AutoClickActionType.Back -> null
                            AutoClickActionType.Recents -> null
                            AutoClickActionType.TextClick -> null
                        },
                        endY = when (normalizedType) {
                            AutoClickActionType.Click -> null
                            AutoClickActionType.Swipe -> (point.endY ?: point.y).coerceAtLeast(0)
                            AutoClickActionType.Home -> null
                            AutoClickActionType.Back -> null
                            AutoClickActionType.Recents -> null
                            AutoClickActionType.TextClick -> null
                        },
                        delayMs = point.delayMs.coerceAtLeast(0L),
                        touchDurationMs = point.touchDurationMs.coerceAtLeast(1L),
                        repeatCount = point.repeatCount.coerceAtLeast(1),
                        textFindRetryCount = point.textFindRetryCount.coerceAtLeast(0),
                        textFindRetryDelayMs = point.textFindRetryDelayMs.coerceAtLeast(0L)
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

            AutoClickActionType.Home -> AutoClickPoint(
                id = nextId,
                x = 0,
                y = 0,
                actionType = AutoClickActionType.Home
            )

            AutoClickActionType.Back -> AutoClickPoint(
                id = nextId,
                x = 0,
                y = 0,
                actionType = AutoClickActionType.Back
            )

            AutoClickActionType.Recents -> AutoClickPoint(
                id = nextId,
                x = 0,
                y = 0,
                actionType = AutoClickActionType.Recents
            )

            AutoClickActionType.TextClick -> AutoClickPoint(
                id = nextId,
                x = 0,
                y = 0,
                actionType = AutoClickActionType.TextClick
            )
        }
    }

    private fun normalizeScheduleRuleHms(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        return if (Regex("""^\d{2}:\d{2}:\d{2}$""").matches(value)) value else null
    }

    private fun formatHms(hour: Int, minute: Int, second: Int): String {
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hour, minute, second)
    }
}
