package com.yemen.watersurvey.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yemen.watersurvey.core.security.PinLockManager

@Composable
fun PinLockScreen(
    onPinVerified: () -> Unit
) {
    val context = LocalContext.current
    val isPinSet = remember { PinLockManager.isPinSet(context) }

    var pinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isSetupMode = !isPinSet
    val title = if (isSetupMode) "إنشاء رمز PIN" else "أدخل رمز PIN"
    val subtitle = if (isSetupMode) {
        "قم بإنشاء رمز PIN مؤلف من 4 أرقام لحماية التطبيق"
    } else {
        "أدخل رمز PIN للمتابعة"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = pinInput,
            onValueChange = { newValue ->
                if (newValue.length <= 4 && newValue.all { it.isDigit() }) {
                    pinInput = newValue
                    errorMessage = null
                }
            },
            label = { Text(if (isSetupMode) "رمز PIN جديد" else "رمز PIN") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPassword) "إخفاء" else "إظهار"
                    )
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(0.8f)
        )

        if (isSetupMode) {
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = confirmPinInput,
                onValueChange = { newValue ->
                    if (newValue.length <= 4 && newValue.all { it.isDigit() }) {
                        confirmPinInput = newValue
                        errorMessage = null
                    }
                },
                label = { Text("تأكيد رمز PIN") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(0.8f)
            )
        }

        errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                when {
                    isSetupMode -> {
                        if (pinInput.length != 4) {
                            errorMessage = "يجب أن يكون الرمز 4 أرقام"
                        } else if (confirmPinInput != pinInput) {
                            errorMessage = "الرمزان غير متطابقين"
                        } else {
                            val success = PinLockManager.setPin(context, pinInput)
                            if (success) {
                                onPinVerified()
                            } else {
                                errorMessage = "فشل في حفظ الرمز"
                            }
                        }
                    }
                    else -> {
                        if (pinInput.length != 4) {
                            errorMessage = "يجب أن يكون الرمز 4 أرقام"
                        } else {
                            val verified = PinLockManager.verifyPin(context, pinInput)
                            if (verified) {
                                onPinVerified()
                            } else {
                                errorMessage = "رمز PIN غير صحيح"
                                pinInput = ""
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(0.8f),
            enabled = if (isSetupMode) pinInput.length == 4 && confirmPinInput.length == 4 else pinInput.length == 4
        ) {
            Text(if (isSetupMode) "حفظ رمز PIN" else "تحقق")
        }
    }
}