package com.yemen.watersurvey.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yemen.watersurvey.data.database.SurveyAppDatabase
import com.yemen.watersurvey.data.entity.SurveyAttachmentEntity
import com.yemen.watersurvey.data.repository.AttachmentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class PhotoAttachmentState(
    val attachments: List<SurveyAttachmentEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
)

class PhotoAttachmentViewModel(application: Application) : AndroidViewModel(application) {

    private val attachmentDao = SurveyAppDatabase.getInstance(application).surveyAttachmentDao()
    private val attachmentRepository = AttachmentRepository(application, attachmentDao)

    private val _uiState = MutableStateFlow(PhotoAttachmentState())
    val uiState: StateFlow<PhotoAttachmentState> = _uiState.asStateFlow()

    fun loadAttachments(surveyUUID: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val attachments = attachmentRepository.getAttachmentsForSurvey(surveyUUID)
                _uiState.update { it.copy(attachments = attachments, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "فشل تحميل الصور: ${e.message}") }
            }
        }
    }

    fun savePhoto(sourceFile: File, surveyUUID: String, recordId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                attachmentRepository.savePhoto(sourceFile, surveyUUID, recordId)
                loadAttachments(surveyUUID)
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = "فشل حفظ الصورة: ${e.message}") }
            } finally {
                if (sourceFile.exists()) {
                    sourceFile.delete()
                }
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun deletePhoto(attachment: SurveyAttachmentEntity) {
        viewModelScope.launch {
            try {
                attachmentRepository.deleteAttachment(attachment)
                loadAttachments(attachment.surveyUUID)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "فشل حذف الصورة: ${e.message}") }
            }
        }
    }
}
