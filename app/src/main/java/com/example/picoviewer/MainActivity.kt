package com.example.picoviewer

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.system.exitProcess

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF64B5F6),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E)
                )
            ) {
                MainScreen(onExit = { finishAndRemoveTask(); exitProcess(0) })
            }
        }
    }
}

@Composable
fun MainScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    var selectedFileData by remember { mutableStateOf<String?>(null) }
    var fileName by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let {
                try {
                    context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst()) {
                            fileName = cursor.getString(nameIndex)
                        }
                    }
                    
                    val content = context.contentResolver.openInputStream(it)?.use { stream ->
                        BufferedReader(InputStreamReader(stream)).readText()
                    }
                    if (content.isNullOrBlank()) {
                        Toast.makeText(context, "File is empty", Toast.LENGTH_SHORT).show()
                    } else {
                        selectedFileData = content
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error opening file: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            IconButton(
                onClick = onExit,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(48.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Exit", tint = Color.White.copy(alpha = 0.5f))
            }

            if (selectedFileData == null) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "RETINA Results",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Button(
                        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.height(60.dp).width(260.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("SELECT RESULTS FILE", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "Note: Use the menu in the picker to 'Show Internal Storage' if you don't see your folders.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(300.dp)
                    )
                }
            } else {
                MetricsScreen(
                    rawData = selectedFileData!!,
                    fileName = fileName,
                    onBack = { selectedFileData = null }
                )
            }
        }
    }
}

@Composable
fun MetricsScreen(rawData: String, fileName: String, onBack: () -> Unit) {
    val allMetrics = remember(rawData) { MetricsParser.parse(rawData) }
    
    val groups = remember(allMetrics) {
        listOf(
            allMetrics.filter { it.key in listOf("cognitiveSpeed", "cognitiveControl", "cognitiveReadiness") },
            allMetrics.filter { it.key in listOf("saccadicOmissionPercentage", "manualOmissionPercentage") },
            allMetrics.filter { it.key in listOf("fixationCommissionPercentage", "saccadicCommissionPercentage", "manualCommissionPercentage") },
            allMetrics.filter { it.key in listOf("saccadicAnticipationPercentage", "manualAnticipationPercentage") },
            allMetrics.filter { it.key in listOf("StandardDeviationManualReactionTime", "StandardDeviationSaccadicReactionTime") },
            allMetrics.filter { it.key in listOf("InterQuartileRangeManualReactionTime", "InterQuartileRangeSaccadicReactionTime") },
            allMetrics.filter { it.key in listOf("medianManualReactionTime", "medianSaccadicReactionTime") },
            allMetrics.filter { it.key in listOf("validManualRTPercentage", "validSaccadicRTPercentage") },
            allMetrics.filter { it.key in listOf("totalTrials", "validTrials") },
            allMetrics.filter { it.key.contains("Gaze off center", ignoreCase = true) || it.key.contains("Fixation Loss", ignoreCase = true) }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "RETINA RESULTS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (fileName.isEmpty()) "Selected File" else fileName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(groups) { group ->
                if (group.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        group.forEach { metric ->
                            MetricTile(
                                metric = metric, 
                                modifier = Modifier.weight(1f),
                                isCognitive = metric.key.startsWith("cognitive")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricTile(metric: Metric, modifier: Modifier = Modifier, isCognitive: Boolean = false) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCognitive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
        ),
        modifier = modifier.height(IntrinsicSize.Min)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = metric.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isCognitive) MaterialTheme.colorScheme.primary else Color.Gray,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = metric.value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                fontSize = if (metric.value.length > 8) 14.sp else 18.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
