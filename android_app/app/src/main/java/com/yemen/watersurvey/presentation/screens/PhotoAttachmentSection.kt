package com.yemen.watersurvey.presentation.screens

import android.Manifest
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yemen.watersurvey.data.entity.SurveyAttachmentEntity
import com.yemen.watersurvey.presentation.screens.camera.CameraPreviewScreen
import com.yemen.watersurvey.presentation.theme.*
import com.yemen.watersurvey.presentation.viewmodel.PhotoAttachmentViewModel
import java.io.File

@Composable
fun PhotoAttachmentSection(
    surveyUUID: String,
    recordId: String,
    viewModel: PhotoAttachmentViewModel,
    isRecordLoading: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCamera by remember { mutableStateOf(false) }
    var permissionDeniedMessage by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDeniedMessage = null
            showCamera = true
        } else {
            permissionDeniedMessage = "يرجى منح إذن استخدام الكاميرا لالتقاط الصور الميدانية."
        }
    }

    LaunchedEffect(surveyUUID) {
        viewModel.loadAttachments(surveyUUID)
    }

    if (showCamera) {
        Dialog(
            onDismissRequest = { showCamera = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            CameraPreviewScreen(
                onPhotoCaptured = { file ->
                    showCamera = false
                    viewModel.savePhoto(file, surveyUUID, recordId)
                },
                onError = { exc ->
                    showCamera = false
                },
                onCancel = {
                    showCamera = false
                }
            )
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "الصور الميدانية",
                    color = Slate100,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                if (uiState.attachments.isNotEmpty()) {
                    Text(
                        text = "(${uiState.attachments.size} صور)",
                        color = Emerald400,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (permissionDeniedMessage != null) {
                Text(
                    text = permissionDeniedMessage!!,
                    color = Red400,
                    fontSize = 12.sp
                )
            }

            if (uiState.error != null) {
                Text(
                    text = uiState.error!!,
                    color = Red400,
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSaving && !isRecordLoading
            ) {
                if (isRecordLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Slate100)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("جاري تحميل بيانات المسح...", fontWeight = FontWeight.Bold)
                } else if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Slate100)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("جاري معالجة وحفظ الصورة...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("التقاط صورة", fontWeight = FontWeight.Bold)
                }
            }

            if (uiState.attachments.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(uiState.attachments, key = { it.attachmentId }) { attachment ->
                        AttachmentThumbnail(
                            attachment = attachment,
                            onDelete = { viewModel.deletePhoto(attachment) }
                        )
                    }
                }
            } else {
                Text("لم يتم التقاط أي صور بعد.", color = Slate400, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun AttachmentThumbnail(
    attachment: SurveyAttachmentEntity,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val bitmap = remember(attachment.localFilePath) {
        try {
            val file = File(context.filesDir, attachment.localFilePath)
            if (file.exists()) {
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
                val sampleSize = maxOf(1, maxOf(boundsOptions.outWidth / 160, boundsOptions.outHeight / 160))
                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                BitmapFactory.decodeFile(file.absolutePath, decodeOptions)?.asImageBitmap()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Slate800)
            .border(1.dp, Emerald500, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = attachment.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Emerald400, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Text("صورة", color = Slate200, fontSize = 10.sp)
            }
        }

        if (onDelete != null) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(20.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "حذف الصورة",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
