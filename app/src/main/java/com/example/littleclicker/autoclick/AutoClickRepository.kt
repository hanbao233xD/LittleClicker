package com.example.littleclicker.autoclick

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

object AutoClickRepository {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    private const val AUTO_CLICK_DIR = "autoclick"
    private const val PROFILE_DIR = "profiles"
    private const val STATE_FILE = "state.json"
    private const val LEGACY_PROFILE_FILE = "profile.json"
    private const val DEFAULT_PROFILE_ID = "default"
    private const val DEFAULT_PROFILE_NAME = "点击配置_1"

    private data class AutoClickStorageState(
        val activeProfileId: String? = null,
    )

    private data class AutoClickPointPayload(
        val id: Int? = null,
        val x: Int? = null,
        val y: Int? = null,
        val actionType: String? = null,
        val endX: Int? = null,
        val endY: Int? = null,
        val delayMs: Long? = null,
        val touchDurationMs: Long? = null,
        val repeatCount: Int? = null,
        val targetText: String? = null,
        val textFindRetryCount: Int? = null,
        val textFindRetryDelayMs: Long? = null,
        val continuousRetry: Boolean? = null,
    )

    private data class AutoClickProfilePayload(
        val id: String? = null,
        val name: String? = null,
        val points: List<AutoClickPointPayload>? = null,
        val cycleCount: Int? = null,
        val layoutLocked: Boolean? = null,
        val runMode: String? = null,
        val loopIntervalDelayMs: Long? = null,
        val clickRandomOffsetPx: Int? = null,
        val randomDelayMs: Long? = null,
        val recordingMode: String? = null,
        val ntpServerHost: String? = null,
        val scheduleRuleHms: String? = null,
        val startAtMillis: Long? = null,
        val updatedAt: Long? = null,
    )

    fun loadActiveProfile(context: Context): AutoClickProfile {
        migrateLegacyProfileIfNeeded(context)

        val profiles = listProfiles(context)
        if (profiles.isEmpty()) {
            val created = AutoClickProfile(
                id = DEFAULT_PROFILE_ID,
                name = DEFAULT_PROFILE_NAME,
                points = emptyList(),
                cycleCount = 1,
                startAtMillis = null,
                updatedAt = System.currentTimeMillis()
            )
            saveProfile(context, created, makeActive = true)
            return created
        }

        val activeId = loadStorageState(context).activeProfileId
        val active = profiles.firstOrNull { it.id == activeId } ?: profiles.first()
        saveStorageState(context, AutoClickStorageState(activeProfileId = active.id))
        return active
    }

    fun listProfiles(context: Context): List<AutoClickProfile> {
        val dir = profilesDir(context)
        if (!dir.exists()) return emptyList()

        return dir.listFiles()
            ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            ?.mapNotNull { file -> runCatching { profileFromJson(file.readText()) }.getOrNull() }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
    }

    fun loadProfile(context: Context, profileId: String): AutoClickProfile? {
        val file = profileFile(context, profileId)
        if (!file.exists()) return null
        return runCatching {
            profileFromJson(file.readText())
        }.getOrNull()
    }

    fun saveProfile(
        context: Context,
        profile: AutoClickProfile,
        makeActive: Boolean = true,
    ) {
        val normalized = profile.copy(
            id = profile.id.ifBlank { DEFAULT_PROFILE_ID },
            name = profile.name.ifBlank { "未命名配置" },
            ntpServerHost = profile.ntpServerHost.ifBlank { DEFAULT_NTP_SERVER_HOST },
            scheduleRuleHms = normalizeScheduleRuleHms(profile.scheduleRuleHms),
            updatedAt = System.currentTimeMillis()
        )

        val file = profileFile(context, normalized.id)
        file.parentFile?.mkdirs()
        file.writeText(profileToJson(normalized))

        if (makeActive) {
            saveStorageState(context, AutoClickStorageState(activeProfileId = normalized.id))
        }
    }

    fun deleteProfile(context: Context, profileId: String): Boolean {
        val file = profileFile(context, profileId)
        if (!file.exists()) return false
        return runCatching { file.delete() }.getOrDefault(false)
    }

    fun getActiveProfileId(context: Context): String? {
        return loadStorageState(context).activeProfileId
    }

    fun setActiveProfileId(context: Context, profileId: String) {
        saveStorageState(context, AutoClickStorageState(activeProfileId = profileId))
    }

    fun profileToJson(profile: AutoClickProfile): String = gson.toJson(profile)

    fun profileFromJson(json: String): AutoClickProfile {
        val payload = gson.fromJson(json, AutoClickProfilePayload::class.java) ?: AutoClickProfilePayload()
        val normalizedPoints = payload.points.orEmpty().mapIndexed { index, point ->
            val x = (point.x ?: 0).coerceAtLeast(0)
            val y = (point.y ?: 0).coerceAtLeast(0)
            val actionType = parseActionType(point.actionType)
            AutoClickPoint(
                id = (point.id ?: (index + 1)).coerceAtLeast(1),
                x = x,
                y = y,
                actionType = actionType,
                endX = if (actionType == AutoClickActionType.Swipe) {
                    (point.endX ?: (x + 200)).coerceAtLeast(0)
                } else {
                    null
                },
                endY = if (actionType == AutoClickActionType.Swipe) {
                    (point.endY ?: y).coerceAtLeast(0)
                } else {
                    null
                },
                delayMs = (point.delayMs ?: 200L).coerceAtLeast(0L),
                touchDurationMs = (point.touchDurationMs ?: 50L).coerceAtLeast(1L),
                repeatCount = (point.repeatCount ?: 1).coerceAtLeast(1),
                targetText = point.targetText ?: "",
                textFindRetryCount = (point.textFindRetryCount ?: DEFAULT_TEXT_FIND_RETRY_COUNT).coerceAtLeast(0),
                textFindRetryDelayMs = (point.textFindRetryDelayMs ?: DEFAULT_TEXT_FIND_RETRY_DELAY_MS).coerceAtLeast(0L),
                continuousRetry = point.continuousRetry ?: false
            )
        }

        return AutoClickProfile(
            id = payload.id?.takeIf { it.isNotBlank() } ?: DEFAULT_PROFILE_ID,
            name = payload.name?.takeIf { it.isNotBlank() } ?: DEFAULT_PROFILE_NAME,
            points = normalizedPoints,
            cycleCount = (payload.cycleCount ?: 1).coerceAtLeast(1),
            layoutLocked = payload.layoutLocked ?: false,
            runMode = parseRunMode(payload.runMode),
            loopIntervalDelayMs = (payload.loopIntervalDelayMs ?: DEFAULT_LOOP_INTERVAL_DELAY_MS).coerceAtLeast(0L),
            clickRandomOffsetPx = (payload.clickRandomOffsetPx ?: DEFAULT_CLICK_RANDOM_OFFSET_PX).coerceAtLeast(0),
            randomDelayMs = (payload.randomDelayMs ?: DEFAULT_RANDOM_DELAY_MS).coerceAtLeast(0L),
            recordingMode = parseRecordingMode(payload.recordingMode),
            ntpServerHost = payload.ntpServerHost?.takeIf { it.isNotBlank() } ?: DEFAULT_NTP_SERVER_HOST,
            scheduleRuleHms = normalizeScheduleRuleHms(payload.scheduleRuleHms),
            startAtMillis = payload.startAtMillis,
            updatedAt = payload.updatedAt ?: System.currentTimeMillis()
        )
    }

    private fun migrateLegacyProfileIfNeeded(context: Context) {
        val legacyFile = File(autoclickDir(context), LEGACY_PROFILE_FILE)
        if (!legacyFile.exists()) return

        val currentProfiles = listProfiles(context)
        if (currentProfiles.isNotEmpty()) {
            return
        }

        val parsedLegacy = runCatching {
            profileFromJson(legacyFile.readText())
        }.getOrElse {
            AutoClickProfile()
        }
        val migrated = parsedLegacy.copy(
            id = if (parsedLegacy.id.isBlank()) DEFAULT_PROFILE_ID else parsedLegacy.id,
            updatedAt = System.currentTimeMillis()
        )

        saveProfile(context, migrated, makeActive = true)
        runCatching { legacyFile.delete() }
    }

    private fun autoclickDir(context: Context): File = File(context.filesDir, AUTO_CLICK_DIR)

    private fun profilesDir(context: Context): File = File(autoclickDir(context), PROFILE_DIR)

    private fun profileFile(context: Context, profileId: String): File {
        val safeId = sanitizeFileName(profileId)
        return File(profilesDir(context), "$safeId.json")
    }

    private fun stateFile(context: Context): File = File(autoclickDir(context), STATE_FILE)

    private fun loadStorageState(context: Context): AutoClickStorageState {
        val file = stateFile(context)
        if (!file.exists()) return AutoClickStorageState()
        return runCatching {
            gson.fromJson(file.readText(), AutoClickStorageState::class.java)
        }.getOrElse {
            AutoClickStorageState()
        }
    }

    private fun saveStorageState(context: Context, state: AutoClickStorageState) {
        val file = stateFile(context)
        file.parentFile?.mkdirs()
        file.writeText(gson.toJson(state))
    }

    private fun sanitizeFileName(raw: String): String {
        if (raw.isBlank()) return DEFAULT_PROFILE_ID
        return raw.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    private fun parseActionType(raw: String?): AutoClickActionType {
        return when {
            raw.equals("Swipe", ignoreCase = true) -> AutoClickActionType.Swipe
            raw.equals("Home", ignoreCase = true) -> AutoClickActionType.Home
            raw.equals("Back", ignoreCase = true) -> AutoClickActionType.Back
            raw.equals("Recents", ignoreCase = true) ||
                raw.equals("Recent", ignoreCase = true) ||
                raw.equals("多任务", ignoreCase = true) -> AutoClickActionType.Recents
            raw.equals("TextClick", ignoreCase = true) -> AutoClickActionType.TextClick
            else -> AutoClickActionType.Click
        }
    }

    private fun parseRunMode(raw: String?): AutoClickRunMode {
        return if (raw.equals("LoopUntilStopped", ignoreCase = true)) {
            AutoClickRunMode.LoopUntilStopped
        } else {
            AutoClickRunMode.RunOnce
        }
    }

    private fun parseRecordingMode(raw: String?): AutoClickRecordingMode {
        return if (raw.equals("RecordOnly", ignoreCase = true)) {
            AutoClickRecordingMode.RecordOnly
        } else {
            AutoClickRecordingMode.RecordAndPassThrough
        }
    }

    private fun normalizeScheduleRuleHms(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        return Regex("""^\d{2}:\d{2}:\d{2}$""")
            .takeIf { it.matches(value) }
            ?.let { value }
    }
}
