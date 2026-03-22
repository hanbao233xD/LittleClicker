package com.example.littleclicker.autoclick

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

object AutoClickRepository {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    private const val AUTO_CLICK_DIR = "autoclick"
    private const val PROFILE_FILE = "profile.json"
    private const val SCRIPT_DIR = "scripts"

    fun loadProfile(context: Context): AutoClickProfile {
        val file = profileFile(context)
        if (!file.exists()) return AutoClickProfile()
        return runCatching {
            profileFromJson(file.readText())
        }.getOrElse {
            AutoClickProfile()
        }
    }

    fun saveProfile(context: Context, profile: AutoClickProfile) {
        val file = profileFile(context)
        file.parentFile?.mkdirs()
        file.writeText(profileToJson(profile))
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

    fun profileFromJson(json: String): AutoClickProfile = gson.fromJson(json, AutoClickProfile::class.java)

    fun draftToJson(draft: ScriptDraft): String = gson.toJson(draft)

    fun draftFromJson(json: String): ScriptDraft = gson.fromJson(json, ScriptDraft::class.java)

    private fun autoclickDir(context: Context): File = File(context.filesDir, AUTO_CLICK_DIR)

    private fun profileFile(context: Context): File = File(autoclickDir(context), PROFILE_FILE)

    private fun scriptsDir(context: Context): File = File(context.filesDir, SCRIPT_DIR)

    private fun draftFile(context: Context, draftId: String): File = File(scriptsDir(context), "$draftId.json")
}
