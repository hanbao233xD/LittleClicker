package com.example.littleclicker.agreement

import android.content.Context
import java.io.File
import java.io.IOException

internal object UserAgreementStore {
    internal const val MARKER_FILE_NAME = "user_agreement_accepted_v1.marker"

    fun isAccepted(context: Context): Boolean = isAccepted(context.filesDir)

    fun markAccepted(context: Context): Result<Unit> = markAccepted(context.filesDir)

    internal fun isAccepted(filesDir: File): Boolean = markerFile(filesDir).isFile

    internal fun markAccepted(filesDir: File): Result<Unit> = runCatching {
        if (!filesDir.isDirectory) {
            throw IOException("Application files directory is unavailable")
        }

        val marker = markerFile(filesDir)
        if (marker.exists()) {
            if (!marker.isFile) {
                throw IOException("Agreement marker path is not a file")
            }
            return@runCatching
        }

        if (!marker.createNewFile() || !marker.isFile) {
            throw IOException("Unable to create agreement marker")
        }
    }

    private fun markerFile(filesDir: File): File = File(filesDir, MARKER_FILE_NAME)
}
