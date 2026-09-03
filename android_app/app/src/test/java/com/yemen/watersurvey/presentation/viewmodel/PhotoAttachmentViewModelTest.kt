package com.yemen.watersurvey.presentation.viewmodel

import android.content.Context
import androidx.room.Room
import com.yemen.watersurvey.data.database.SurveyAppDatabase
import com.yemen.watersurvey.data.entity.SurveyAttachmentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class PhotoAttachmentViewModelTest {

    private lateinit var viewModel: PhotoAttachmentViewModel
    private lateinit var database: SurveyAppDatabase
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var context: Context

    @Before
    fun setup() {
        testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        context = RuntimeEnvironment.getApplication()

        try {
            val instanceField = SurveyAppDatabase::class.java.getDeclaredField("INSTANCE")
            instanceField.isAccessible = true
            instanceField.set(null, null)
        } catch (e: Exception) {
            // Ignore
        }

        database = Room.inMemoryDatabaseBuilder(context, SurveyAppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val instanceField = SurveyAppDatabase::class.java.getDeclaredField("INSTANCE")
        instanceField.isAccessible = true
        instanceField.set(null, database)

        viewModel = PhotoAttachmentViewModel(context as android.app.Application)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()

        try {
            val instanceField = SurveyAppDatabase::class.java.getDeclaredField("INSTANCE")
            instanceField.isAccessible = true
            instanceField.set(null, null)
        } catch (e: Exception) {
            // Ignore
        }
    }

    @Test
    fun testLoadAttachmentsEmptyInitially() = runTest {
        val surveyUUID = UUID.randomUUID().toString()
        viewModel.loadAttachments(surveyUUID)
        viewModel.uiState.first { !it.isLoading }

        val state = viewModel.uiState.value
        assertTrue("Attachments should be empty initially", state.attachments.isEmpty())
        assertFalse("isLoading should be false", state.isLoading)
        assertNull("Error should be null", state.error)
    }

    @Test
    fun testLoadAttachmentsWhenPresent() = runTest {
        val surveyUUID = UUID.randomUUID().toString()
        val recordId = "REC-123"

        val entity = SurveyAttachmentEntity(
            attachmentId = UUID.randomUUID().toString(),
            surveyUUID = surveyUUID,
            recordId = recordId,
            attachmentType = "PHOTO",
            localFilePath = "attachments/ATT_test.jpg",
            fileName = "ATT_test.jpg",
            fileSizeBytes = 1024L,
            fileSha256 = "abc123sha",
            capturedAt = "2026-09-01 12:00:00",
            sourcePackageId = ""
        )
        database.surveyAttachmentDao().insertAttachment(entity)

        viewModel.loadAttachments(surveyUUID)
        viewModel.uiState.first { !it.isLoading }

        val state = viewModel.uiState.value
        assertEquals("Should retrieve exactly 1 attachment", 1, state.attachments.size)
        assertEquals("attachmentId should match", entity.attachmentId, state.attachments[0].attachmentId)
    }

    @Test
    fun testDeletePhoto() = runTest {
        val surveyUUID = UUID.randomUUID().toString()
        val recordId = "REC-456"

        val entity = SurveyAttachmentEntity(
            attachmentId = UUID.randomUUID().toString(),
            surveyUUID = surveyUUID,
            recordId = recordId,
            attachmentType = "PHOTO",
            localFilePath = "attachments/ATT_delete_test.jpg",
            fileName = "ATT_delete_test.jpg",
            fileSizeBytes = 2048L,
            fileSha256 = "del123sha",
            capturedAt = "2026-09-01 13:00:00",
            sourcePackageId = ""
        )
        database.surveyAttachmentDao().insertAttachment(entity)

        viewModel.loadAttachments(surveyUUID)
        viewModel.uiState.first { it.attachments.isNotEmpty() }
        assertEquals(1, viewModel.uiState.value.attachments.size)

        // Execute delete
        viewModel.deletePhoto(entity)
        viewModel.uiState.first { it.attachments.isEmpty() }

        val afterDelete = viewModel.uiState.value.attachments
        assertTrue("Attachments list should be empty after deletion", afterDelete.isEmpty())
        val dbAttachments = database.surveyAttachmentDao().getAttachmentsForSurvey(surveyUUID)
        assertTrue("Database should have 0 attachments for this survey", dbAttachments.isEmpty())
    }
}
