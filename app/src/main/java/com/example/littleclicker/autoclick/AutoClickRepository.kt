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
    private const val SCRIPT_DIR = "scripts"
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
    )

    private data class AutoClickProfilePayload(
        val id: String? = null,
        val name: String? = null,
        val points: List<AutoClickPointPayload>? = null,
        val cycleCount: Int? = null,
        val runMode: String? = null,
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

    fun listDrafts(context: Context): List<ScriptDraft> {
        val dir = scriptsDir(context)
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            ?.mapNotNull { file ->
                runCatching { draftFromJson(file.readText()) }.getOrNull()
            }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
    }

    fun createDraft(context: Context, name: String): ScriptDraft {
        val now = System.currentTimeMillis()
        val draft = ScriptDraft(
            id = "draft_${now}_${(1000..9999).random()}",
            name = name.ifBlank { "未命名草稿" },
            actions = emptyList(),
            createdAt = now,
            updatedAt = now
        )
        saveDraft(context, draft)
        return draft
    }

    fun saveDraft(context: Context, draft: ScriptDraft) {
        val file = draftFile(context, draft.id)
        file.parentFile?.mkdirs()
        file.writeText(draftToJson(draft))
    }

    fun loadDraft(context: Context, draftId: String): ScriptDraft? {
        val file = draftFile(context, draftId)
        if (!file.exists()) return null
        return runCatching {
            draftFromJson(file.readText())
        }.getOrNull()
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
                repeatCount = (point.repeatCount ?: 1).coerceAtLeast(1)
            )
        }

        return AutoClickProfile(
            id = payload.id?.takeIf { it.isNotBlank() } ?: DEFAULT_PROFILE_ID,
            name = payload.name?.takeIf { it.isNotBlank() } ?: DEFAULT_PROFILE_NAME,
            points = normalizedPoints,
            cycleCount = (payload.cycleCount ?: 1).coerceAtLeast(1),
            runMode = parseRunMode(payload.runMode),
            startAtMillis = payload.startAtMillis,
            updatedAt = payload.updatedAt ?: System.currentTimeMillis()
        )
    }

    fun draftToJson(draft: ScriptDraft): String = gson.toJson(draft)

    fun draftFromJson(json: String): ScriptDraft = gson.fromJson(json, ScriptDraft::class.java)

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

    private fun scriptsDir(context: Context): File = File(context.filesDir, SCRIPT_DIR)

    private fun draftFile(context: Context, draftId: String): File = File(scriptsDir(context), "$draftId.json")

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
        return if (raw.equals("Swipe", ignoreCase = true)) {
            AutoClickActionType.Swipe
        } else {
            AutoClickActionType.Click
        }
    }

    private fun parseRunMode(raw: String?): AutoClickRunMode {
        return if (raw.equals("LoopUntilStopped", ignoreCase = true)) {
            AutoClickRunMode.LoopUntilStopped
        } else {
            AutoClickRunMode.RunOnce
        }
    }
}
