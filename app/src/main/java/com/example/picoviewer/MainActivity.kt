package com.example.picoviewer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
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
import androidx.compose.material.icons.filled.CloudUpload
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
import com.example.picoviewer.BuildConfig
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import kotlin.system.exitProcess

/** True when the picker filename is explicitly `.txt` (CSV is commonly `text/plain` too). */
private fun isTxtFileName(displayName: String?): Boolean =
    displayName?.trim()?.lowercase(Locale.US)?.endsWith(".txt") == true

private fun isCsvFileName(displayName: String?): Boolean =
    displayName?.trim()?.lowercase(Locale.US)?.endsWith(".csv") == true

/**
 * Where RETINA Task stores results on device internal storage (`…/Android/data/…/files/Results`).
 * Passed to the system SAF picker so it can open here first (API 26+; OEMs may ignore the hint).
 */
private val retinaTaskResultsDocumentsUri: Uri = DocumentsContract.buildDocumentUri(
    "com.android.externalstorage.documents",
    "primary:Android/data/com.RetinaTek.RETINATask/files/Results",
)

private class OpenDocumentStartingAtResults : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        return super.createIntent(context, input).apply {
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, retinaTaskResultsDocumentsUri)
        }
    }
}

/**
 * RETINA Results viewer UI.
 *
 * Flow: [MainScreen] opens the system file picker, reads the chosen file as plain text, then
 * [MetricsScreen] parses it with [MetricsParser] and lays out tiles in a fixed order (cognitive row,
 * then saccadic, manual, miscellaneous). See [MetricData.kt] for parsing and label normalization.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dark theme tuned for VR / kiosk-style devices; root composable is MainScreen.
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF64B5F6),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E)
                )
            ) {
                // Close removes the task and exits the process (handy on headsets with no app switcher).
                MainScreen(onExit = { finishAndRemoveTask(); exitProcess(0) })
            }
        }
    }
}

/**
 * Landing UI: pick a metrics text file, or show [MetricsScreen] once [selectedFileData] is loaded.
 */
@Composable
fun MainScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    var selectedFileData by remember { mutableStateOf<String?>(null) }
    var fileName by remember { mutableStateOf("") }
    var selectedTxtUri by remember { mutableStateOf<Uri?>(null) }
    var pairedCsvUri by remember { mutableStateOf<Uri?>(null) }
    var pairedCsvDisplayName by remember { mutableStateOf<String?>(null) }

    // SAF document picker — works with Drive, Downloads, etc.; no hard-coded paths.
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = OpenDocumentStartingAtResults(),
        onResult = { uri: Uri? ->
            uri?.let {
                try {
                    context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst()) {
                            fileName = cursor.getString(nameIndex) ?: ""
                        }
                    }
                    if (!isTxtFileName(fileName)) {
                        Toast.makeText(context, "Only .txt results files are supported", Toast.LENGTH_LONG).show()
                        return@let
                    }
                    selectedTxtUri = it
                    pairedCsvUri = resolveCsvSiblingUri(context, it, fileName)
                    pairedCsvDisplayName = pairedCsvUri?.let { csvUri -> queryUriDisplayName(context, csvUri) }
                    // Entire file is read as UTF-8 text; parser expects "Label: value" lines.
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

    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = OpenDocumentStartingAtResults(),
        onResult = { uri: Uri? ->
            uri?.let {
                try {
                    val csvName = queryUriDisplayName(context, it).orEmpty()
                    if (!isCsvFileName(csvName)) {
                        Toast.makeText(context, "Only .csv results files are supported", Toast.LENGTH_LONG).show()
                        return@let
                    }
                    pairedCsvUri = it
                    pairedCsvDisplayName = csvName
                    Toast.makeText(context, "CSV selected: $csvName", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error opening CSV: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        },
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
                        onClick = { filePickerLauncher.launch(arrayOf("text/plain")) },
                        modifier = Modifier.height(60.dp).width(260.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("SELECT RESULTS FILE", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "The picker should open in the RETINA Task Results folder. If not, use the menu to show internal storage, then browse Android/data/com.RetinaTek.RETINATask/files/Results.",
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
                    txtUri = selectedTxtUri!!,
                    csvUri = pairedCsvUri,
                    csvDisplayName = pairedCsvDisplayName,
                    onPickCsv = {
                        csvPickerLauncher.launch(
                            arrayOf(
                                "text/csv",
                                "text/comma-separated-values",
                                "application/vnd.ms-excel",
                                "text/plain",
                                "*/*",
                            ),
                        )
                    },
                    onBack = {
                        selectedFileData = null
                        selectedTxtUri = null
                        pairedCsvUri = null
                        pairedCsvDisplayName = null
                    },
                )
            }
        }
    }
}

// Canonical key order for layout (must match keys produced by MetricsParser.mapToInternalKey).
// File order is ignored; we always show metrics in this order when present.

private val cognitiveKeyOrder = listOf("cog_readiness", "cog_control", "cog_speed")
private val saccadicKeyOrder = listOf(
    "omission_saccadic",
    "commission_saccadic",
    "anticipation_saccadic",
    "sd_saccadic",
    "iqr_saccadic",
    "rt_saccadic",
    "valid_saccadic",
)
private val manualKeyOrder = listOf(
    "omission_manual",
    "commission_manual",
    "anticipation_manual",
    "sd_manual",
    "iqr_manual",
    "rt_manual",
    "valid_manual",
)

/**
 * Parsed metrics grouped for display: one row for cognitive (three tiles), then scrolling sections
 * for saccadic, manual, and misc. [Metric.tileLabel] carries user-facing titles (parallel for
 * saccadic vs manual).
 */
@Composable
fun MetricsScreen(
    rawData: String,
    fileName: String,
    txtUri: Uri,
    csvUri: Uri?,
    csvDisplayName: String?,
    onPickCsv: () -> Unit,
    onBack: () -> Unit,
) {
    val allMetrics = remember(rawData) { MetricsParser.parse(rawData) }

    // Last wins if duplicate keys ever appear in one file.
    val byKey = remember(allMetrics) { allMetrics.associateBy { it.key } }
    val cognitiveRow = remember(byKey) { cognitiveKeyOrder.mapNotNull { byKey[it] } }
    val saccadicList = remember(byKey) { saccadicKeyOrder.mapNotNull { byKey[it] } }
    val manualList = remember(byKey) { manualKeyOrder.mapNotNull { byKey[it] } }
    // Everything classified as MISC in the parser (fixation no-go, trials, gaze, unknowns, …).
    val miscList = remember(allMetrics) {
        allMetrics
            .filter { it.section == MetricSection.MISC }
            .distinctBy { it.key }
            .sortedWith(compareBy({ it.sectionOrder }, { it.label.lowercase() }))
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
            // Single horizontal row: Readiness | Control | Speed (only tiles that exist in file).
            if (cognitiveRow.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        cognitiveRow.forEach { metric ->
                            MetricTile(
                                metric = metric,
                                modifier = Modifier.weight(1f),
                                isCognitive = true,
                            )
                        }
                    }
                }
            }

            // Full-width tiles, same label pattern as Manual (see Metric.displayLabel in parser).
            if (saccadicList.isNotEmpty()) {
                item {
                    SectionTitle("Saccadic")
                }
                items(saccadicList) { metric ->
                    MetricTile(
                        metric = metric,
                        modifier = Modifier.fillMaxWidth(),
                        isCognitive = false,
                    )
                }
            }

            if (manualList.isNotEmpty()) {
                item {
                    SectionTitle("Manual")
                }
                items(manualList) { metric ->
                    MetricTile(
                        metric = metric,
                        modifier = Modifier.fillMaxWidth(),
                        isCognitive = false,
                    )
                }
            }

            if (miscList.isNotEmpty()) {
                item {
                    SectionTitle("Miscellaneous")
                }
                items(miscList) { metric ->
                    MetricTile(
                        metric = metric,
                        modifier = Modifier.fillMaxWidth(),
                        isCognitive = false,
                    )
                }
            }

            item {
                UploadToDriveSection(
                    txtUri = txtUri,
                    txtFileName = fileName,
                    csvUri = csvUri,
                    csvDisplayName = csvDisplayName,
                    onPickCsv = onPickCsv,
                )
            }
        }
    }
}

/** POST JSON/base64 to [BuildConfig.UPLOAD_ENDPOINT_URL] (Drive ingest script); shows CSV sibling status. */
@Composable
private fun UploadToDriveSection(
    txtUri: Uri,
    txtFileName: String,
    csvUri: Uri?,
    csvDisplayName: String?,
    onPickCsv: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uploading by remember { mutableStateOf(false) }
    val endpoint = BuildConfig.UPLOAD_ENDPOINT_URL
    val secret = BuildConfig.UPLOAD_SHARED_SECRET

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    "Wi‑Fi upload",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Text(
                if (csvUri != null && !csvDisplayName.isNullOrBlank()) {
                    "CSV paired: $csvDisplayName"
                } else {
                    "No nearby CSV was auto-detected. Select the matching CSV manually so TXT and CSV upload together."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
            )
            if (csvUri == null) {
                OutlinedButton(
                    onClick = onPickCsv,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("SELECT MATCHING CSV", fontWeight = FontWeight.Bold)
                }
            }
            when {
                endpoint.isBlank() -> {
                    Text(
                        "Not configured: set UPLOAD_ENDPOINT_URL (HTTPS Web App URL) and UPLOAD_SHARED_SECRET in app/build.gradle.kts. Deploy scripts/google-apps-script-upload.gs to Drive.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )
                }
                else -> {
                    Button(
                        onClick = {
                            if (uploading) return@Button
                            uploading = true
                            scope.launch {
                                val csvUploadName = when {
                                    csvUri == null -> null
                                    !csvDisplayName.isNullOrBlank() -> csvDisplayName
                                    else -> txtFileName.replace(Regex("(?i)\\.txt$"), ".csv")
                                }
                                val result = ResultsUploader.upload(
                                    context = context,
                                    endpointHttpsUrl = endpoint,
                                    sharedSecret = secret,
                                    txtUri = txtUri,
                                    txtFileName = txtFileName,
                                    csvUri = csvUri,
                                    csvFileName = csvUploadName,
                                )
                                uploading = false
                                when (result) {
                                    is ResultsUploader.UploadResult.Success ->
                                        Toast.makeText(context, "Upload succeeded", Toast.LENGTH_LONG).show()
                                    is ResultsUploader.UploadResult.Failure ->
                                        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = !uploading,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (uploading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 3.dp,
                            )
                        } else {
                            Text("Upload to server / Drive folder", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/** Section heading above saccadic / manual / misc lists in the LazyColumn. */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

/** One metric: label ([Metric.tileLabel]) + value; cognitive tiles use a highlighted card color. */
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
                text = metric.tileLabel,
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
