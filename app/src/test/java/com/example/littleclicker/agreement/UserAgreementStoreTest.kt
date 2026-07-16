package com.example.littleclicker.agreement

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UserAgreementStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun markAcceptedCreatesVersionedMarker() {
        val filesDir = temporaryFolder.newFolder("files")

        assertFalse(UserAgreementStore.isAccepted(filesDir))

        UserAgreementStore.markAccepted(filesDir).getOrThrow()

        assertTrue(UserAgreementStore.isAccepted(filesDir))
        assertTrue(File(filesDir, UserAgreementStore.MARKER_FILE_NAME).isFile)
    }

    @Test
    fun markAcceptedIsIdempotent() {
        val filesDir = temporaryFolder.newFolder("files")

        UserAgreementStore.markAccepted(filesDir).getOrThrow()
        UserAgreementStore.markAccepted(filesDir).getOrThrow()

        assertTrue(UserAgreementStore.isAccepted(filesDir))
    }

    @Test
    fun markAcceptedFailsWhenFilesDirectoryIsUnavailable() {
        val invalidFilesDir = temporaryFolder.newFile("files-as-file")

        val result = UserAgreementStore.markAccepted(invalidFilesDir)

        assertTrue(result.isFailure)
        assertFalse(UserAgreementStore.isAccepted(invalidFilesDir))
    }
}
