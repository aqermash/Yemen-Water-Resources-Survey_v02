package com.yemen.watersurvey.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yemen.watersurvey.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyFormsScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToNewWell: () -> Unit = {},
    onNavigateToNewSpring: () -> Unit = {},
    onNavigateToNewDam: () -> Unit = {}
) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "استمارات المسح الميداني",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Slate900,
                    titleContentColor = Slate100,
                    navigationIconContentColor = Slate100
                )
            )
        },
        containerColor = Slate950
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate900),
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = Emerald400,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "دليل الاستمارات المعتمدة",
                            color = Slate100,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "اختر نوع المنشأة المائية لبدء مسح ميداني جديد باستخدام الاستمارة المخصصة والمطابقة للمحددات المعتمدة.",
                            color = Slate400,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Button(
                onClick = onNavigateToNewWell,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("مسح بئر جديد (New Well Survey)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onNavigateToNewSpring,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Cyan500)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Slate950)
                Spacer(modifier = Modifier.width(8.dp))
                Text("مسح عين / ينبوع جديد (New Spring Survey)", color = Slate950, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onNavigateToNewDam,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Amber600)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("مسح سد / حاجز مائي جديد (New Dam Survey)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

