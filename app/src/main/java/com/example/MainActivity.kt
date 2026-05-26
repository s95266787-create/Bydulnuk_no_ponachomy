package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.Alarm
import com.example.service.AlarmStateHolder
import com.example.service.AlarmSoundService
import com.example.ui.AlarmViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainVoiceAlarmApp()
            }
        }
    }
}

@Composable
fun MainVoiceAlarmApp() {
    val context = LocalContext.current
    val viewModel: AlarmViewModel = viewModel()
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    val isRinging by AlarmStateHolder.isRinging.collectAsStateWithLifecycle()
    val activeAlarmId by AlarmStateHolder.activeAlarmId.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }

    // State for local record preview playing
    var previewPlayer: MediaPlayer? by remember { mutableStateOf(null) }
    var previewPlayingPath by remember { mutableStateOf<String?>(null) }

    fun playLocalPreview(path: String) {
        try {
            previewPlayer?.release()
            val player = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                start()
            }
            previewPlayer = player
            previewPlayingPath = path
            player.setOnCompletionListener {
                previewPlayingPath = null
                previewPlayer = null
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to play preview", e)
            Toast.makeText(context, "Помилка відтворення запису", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopLocalPreview() {
        try {
            previewPlayer?.stop()
            previewPlayer?.release()
            previewPlayer = null
            previewPlayingPath = null
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to stop preview", e)
        }
    }

    // Standard Android runtime permissions request setup
    val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
    }

    var micPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        micPermissionGranted = results[Manifest.permission.RECORD_AUDIO] == true
    }

    LaunchedEffect(Unit) {
        val missing = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CosmicNightBg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = CometAccent,
                contentColor = CosmicNightBg,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .padding(bottom = 16.dp, end = 8.dp)
                    .testTag("add_alarm_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Додати будильник")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Додати", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                // Customized star header
                AppHeader()

                Spacer(modifier = Modifier.height(16.dp))

                if (alarms.isEmpty()) {
                    EmptyAlarmsState(onAddClick = { showAddDialog = true })
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 100.dp)
                    ) {
                        items(alarms, key = { it.id }) { alarm ->
                            AlarmCard(
                                alarm = alarm,
                                onToggleEnabled = { viewModel.toggleAlarm(alarm) },
                                onDelete = { viewModel.deleteAlarm(alarm) },
                                onPreviewSound = {
                                    if (alarm.selectedSoundType == 1 && alarm.customSoundPath != null) {
                                        if (previewPlayingPath == alarm.customSoundPath) {
                                            stopLocalPreview()
                                        } else {
                                            playLocalPreview(alarm.customSoundPath)
                                        }
                                    } else if (alarm.selectedSoundType == 2 && alarm.customSoundPath != null) {
                                        if (previewPlayingPath == alarm.customSoundPath) {
                                            stopLocalPreview()
                                        } else {
                                            playLocalPreview(alarm.customSoundPath)
                                        }
                                    } else {
                                        Toast.makeText(context, "Вбудований звук тестується через будильник", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                isPreviewing = previewPlayingPath != null && previewPlayingPath == alarm.customSoundPath
                            )
                        }
                    }
                }
            }

            // High intensity full screen trigger alert
            if (isRinging) {
                val ringingAlarm = alarms.find { it.id == activeAlarmId }
                FullScreenRingScreen(
                    alarm = ringingAlarm,
                    onDismiss = {
                        AlarmSoundService.stop(context)
                        stopLocalPreview()
                    }
                )
            }
        }
    }

    if (showAddDialog) {
        AddAlarmDialog(
            micPermissionGranted = micPermissionGranted,
            onRequestMicPermission = {
                permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            },
            onDismiss = {
                showAddDialog = false
                viewModel.clearRecording()
                stopLocalPreview()
            },
            onSave = { hour, minute, soundType, builtInSound, customPath, repeatDays, label ->
                viewModel.addAlarm(hour, minute, soundType, builtInSound, customPath, repeatDays, label)
                showAddDialog = false
                viewModel.clearRecording()
            },
            viewModel = viewModel,
            previewPlayingPath = previewPlayingPath,
            onPlayPreview = { path -> playLocalPreview(path) },
            onStopPreview = { stopLocalPreview() }
        )
    }
}

@Composable
fun AppHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Звуковий",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = CometSecondary,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "Будильник 🔔",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = CometAccent,
                letterSpacing = 0.5.sp
            )
        }

        // Mini clock widget displaying current time
        var currentTimeText by remember { mutableStateOf("00:00") }
        LaunchedEffect(Unit) {
            while (true) {
                val calendar = Calendar.getInstance()
                val hour = String.format("%02d", calendar.get(Calendar.HOUR_OF_DAY))
                val min = String.format("%02d", calendar.get(Calendar.MINUTE))
                currentTimeText = "$hour:$min"
                delay(10000)
            }
        }

        Box(
            modifier = Modifier
                .background(CosmicNightSurface, RoundedCornerShape(16.dp))
                .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "Current Time",
                    tint = CometSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = currentTimeText,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun EmptyAlarmsState(onAddClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 120.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .background(CometPrimary.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Alarm,
                contentDescription = "No Alarms",
                tint = CometSecondary,
                modifier = Modifier.size(64.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Будильників не створено",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Натисніть кнопку 'Додати' нижче, щоб налаштувати свій перший звуковий чи голосовий будильник.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
fun AlarmCard(
    alarm: Alarm,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit,
    onPreviewSound: () -> Unit,
    isPreviewing: Boolean
) {
    val formattedTime = String.format("%02d:%02d", alarm.hour, alarm.minute)
    val ukrDaysAbbr = listOf("Нд", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб") // index 0 (Sun=1)..6 (Sat=7)
    val ukrDaysFull = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Нд")
    // Map Calendar constant Sunday=1, Monday=2.. to list display
    // UI displays Monday-Sunday
    val mappedDayNumbers = listOf(2, 3, 4, 5, 6, 7, 1)

    val activeDays = alarm.getRepeatDaysSet()

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("alarm_card_${alarm.id}"),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (alarm.isEnabled) CosmicNightSurface else CosmicNightSurface.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    if (alarm.label.isNotEmpty()) {
                        Text(
                            text = alarm.label,
                            color = CometAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formattedTime,
                            fontSize = 36.sp,
                            color = if (alarm.isEnabled) TextPrimary else TextSecondary,
                            fontWeight = FontWeight.ExtraBold
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        // Render Sound Type Label
                        val soundLabel = when (alarm.selectedSoundType) {
                            1 -> "🎙️ Голос"
                            2 -> "📁 Аудіо"
                            else -> "🔔 " + when (alarm.selectedBuiltInSound) {
                                "bell" -> "Дзвінок"
                                "melodic" -> "Мелодія"
                                "digital" -> "Синтез"
                                else -> "Стандарт"
                            }
                        }

                        Box(
                            modifier = Modifier
                                .background(CometPrimary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = soundLabel,
                                color = CometSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Switch control
                Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = { onToggleEnabled() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CosmicNightBg,
                        checkedTrackColor = CometAccent,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = CosmicNightSurfaceVariant
                    ),
                    modifier = Modifier.testTag("alarm_switch_${alarm.id}")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Repeating circles
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ukrDaysFull.forEachIndexed { index, name ->
                        val dayNum = mappedDayNumbers[index]
                        val isActive = activeDays.contains(dayNum)

                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    color = if (isActive && alarm.isEnabled) CometPrimary else if (isActive) BorderColor else Color.Transparent,
                                    shape = CircleShape
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isActive) Color.Transparent else BorderColor,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name,
                                color = if (isActive) TextPrimary else TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Play recorded Sound test test / Delete icons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (alarm.selectedSoundType != 0 && alarm.customSoundPath != null) {
                        IconButton(
                            onClick = onPreviewSound,
                            modifier = Modifier
                                .size(34.dp)
                                .background(CosmicNightSurfaceVariant, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = "Послухати",
                                tint = if (isPreviewing) CoralActive else CometSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(34.dp)
                            .background(CoralActive.copy(alpha = 0.15f), CircleShape)
                            .testTag("delete_alarm_button_${alarm.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Видалити",
                            tint = CoralActive,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddAlarmDialog(
    micPermissionGranted: Boolean,
    onRequestMicPermission: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (hour: Int, minute: Int, soundType: Int, builtInSound: String, customPath: String?, repeatDays: Set<Int>, label: String) -> Unit,
    viewModel: AlarmViewModel,
    previewPlayingPath: String?,
    onPlayPreview: (String) -> Unit,
    onStopPreview: () -> Unit
) {
    val context = LocalContext.current
    var hour by remember { mutableStateOf(12) }
    var minute by remember { mutableStateOf(0) }
    var label by remember { mutableStateOf("") }

    // Sound customization
    var selectedSoundType by remember { mutableStateOf(0) } // 0=BuiltIn, 1=VoiceRec, 2=Picked
    var selectedBuiltInSound by remember { mutableStateOf("bell") }

    // Repeat selection Set
    var selectedDays by remember { mutableStateOf(emptySet<Int>()) }
    val mappedDayNumbers = listOf(2, 3, 4, 5, 6, 7, 1) // Mon..Sun Calendar codes
    val ukrDaysFull = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Нд")

    // Voice record states
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val recordedPath by viewModel.recordedFilePath.collectAsStateWithLifecycle()

    // File picking state
    var pickedMediaUriString by remember { mutableStateOf<String?>(null) }
    var pickedMediaName by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            // Persist locally
            val copiedPath = viewModel.savePickedUriToInternalFiles(uri)
            if (copiedPath != null) {
                pickedMediaUriString = copiedPath
                // Get display name
                var name = "Обраний файл.mp3"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIndex)
                    }
                }
                pickedMediaName = name
            } else {
                Toast.makeText(context, "Не вдалося імпортувати файл", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(CosmicNightBg)
                .padding(top = 32.dp),
            color = CosmicNightBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header of dialog
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Новий будильник",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Назад", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // 1. Sleek Time Selector Component
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CosmicNightSurface, RoundedCornerShape(16.dp))
                                .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Оберіть час:",
                                color = TextSecondary,
                                fontSize = 14.sp,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Hours Adjustment Column
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(onClick = { hour = (hour + 1) % 24 }) {
                                        Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Година +", tint = CometSecondary)
                                    }
                                    Text(
                                        text = String.format("%02d", hour),
                                        fontSize = 48.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = CometAccent
                                    )
                                    IconButton(onClick = { hour = if (hour == 0) 23 else hour - 1 }) {
                                        Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Година -", tint = CometSecondary)
                                    }
                                }

                                Text(
                                    text = ":",
                                    fontSize = 44.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                )

                                // Minutes Adjustment Column
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(onClick = { minute = (minute + 5) % 60 }) {
                                        Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Хвилина +", tint = CometSecondary)
                                    }
                                    Text(
                                        text = String.format("%02d", minute),
                                        fontSize = 48.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = CometAccent
                                    )
                                    IconButton(onClick = { minute = if (minute < 5) 55 else minute - 5 }) {
                                        Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Хвилина -", tint = CometSecondary)
                                    }
                                }
                            }
                        }
                    }

                    // 2. Repeat Selectors Row
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CosmicNightSurface, RoundedCornerShape(16.dp))
                                .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Повторювати за днями тижня:",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                ukrDaysFull.forEachIndexed { idx, name ->
                                    val dayCode = mappedDayNumbers[idx]
                                    val isSelected = selectedDays.contains(dayCode)

                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(
                                                color = if (isSelected) CometPrimary else CosmicNightSurfaceVariant,
                                                shape = CircleShape
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) Color.Transparent else BorderColor,
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                selectedDays = if (isSelected) {
                                                    selectedDays - dayCode
                                                } else {
                                                    selectedDays + dayCode
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = name,
                                            color = if (isSelected) TextPrimary else TextSecondary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. Sound Selector Component
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CosmicNightSurface, RoundedCornerShape(16.dp))
                                .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Звук повідомлення / будильника:",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Sound style tabs picker
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CosmicNightSurfaceVariant, RoundedCornerShape(10.dp))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                val soundTypes = listOf("Вбудований", "Записати", "З пристрою")
                                soundTypes.forEachIndexed { index, label ->
                                    val selected = selectedSoundType == index
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(
                                                color = if (selected) CometPrimary else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { selectedSoundType = index }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (selected) TextPrimary else TextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Sub forms based on tab selection
                            when (selectedSoundType) {
                                0 -> { // Built in
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val builtInOptions = listOf(
                                            "bell" to "🔔 Магічний дзвіночок",
                                            "melodic" to "🎵 Приємна мелодія",
                                            "digital" to "🚨 Електронний синтезатор"
                                        )
                                        builtInOptions.forEach { (code, name) ->
                                            val isSelected = selectedBuiltInSound == code
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        color = if (isSelected) CometPrimary.copy(alpha = 0.1f) else Color.Transparent,
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (isSelected) CometPrimary else BorderColor,
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable { selectedBuiltInSound = code }
                                                    .padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(text = name, color = if (isSelected) TextPrimary else TextSecondary, fontSize = 14.sp)
                                                if (isSelected) {
                                                    Icon(imageVector = Icons.Default.Check, contentDescription = "Обрано", tint = CometAccent)
                                                }
                                            }
                                        }
                                    }
                                }
                                1 -> { // Voice recording
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        if (!micPermissionGranted) {
                                            Text(
                                                text = "Для запису голосу потрібен дозвіл на мікрофон.",
                                                color = CoralActive,
                                                fontSize = 13.sp,
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Button(
                                                onClick = onRequestMicPermission,
                                                colors = ButtonDefaults.buttonColors(containerColor = CometPrimary)
                                            ) {
                                                Text("Надати дозвіл")
                                            }
                                        } else {
                                            // Handle record state visualization
                                            if (isRecording) {
                                                MicrophonePulseAnimation()
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text(
                                                    text = "Йде запис вашої фрази...",
                                                    color = CoralActive,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                                Spacer(modifier = Modifier.height(12.dp))
                                                IconButton(
                                                    onClick = { viewModel.stopRecording() },
                                                    modifier = Modifier
                                                        .size(54.dp)
                                                        .background(CoralActive, CircleShape)
                                                ) {
                                                    Icon(imageVector = Icons.Default.Stop, contentDescription = "Зупинити", tint = TextPrimary)
                                                }
                                            } else {
                                                if (recordedPath == null) {
                                                    IconButton(
                                                        onClick = { viewModel.startRecording() },
                                                        modifier = Modifier
                                                            .size(64.dp)
                                                            .background(CometPrimary, CircleShape)
                                                    ) {
                                                        Icon(imageVector = Icons.Default.Mic, contentDescription = "Запис", tint = TextPrimary, modifier = Modifier.size(32.dp))
                                                    }
                                                    Spacer(modifier = Modifier.height(10.dp))
                                                    Text(text = "Натисніть mic, щоб записати свій голос", color = TextSecondary, fontSize = 12.sp)
                                                } else {
                                                    // File is recorded!
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(CosmicNightSurfaceVariant, RoundedCornerShape(10.dp))
                                                            .padding(12.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(imageVector = Icons.Default.VolumeUp, contentDescription = "Голосовий запис", tint = MintSuccess)
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text(text = "Запис збережено", color = TextPrimary, fontSize = 14.sp)
                                                        }

                                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            val isPreviewingThis = previewPlayingPath == recordedPath
                                                            IconButton(
                                                                onClick = {
                                                                    if (isPreviewingThis) {
                                                                        onStopPreview()
                                                                    } else {
                                                                        recordedPath?.let { onPlayPreview(it) }
                                                                    }
                                                                },
                                                                modifier = Modifier
                                                                    .size(34.dp)
                                                                    .background(CosmicNightBg, CircleShape)
                                                            ) {
                                                                Icon(
                                                                    imageVector = if (isPreviewingThis) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                                    contentDescription = "Слухати",
                                                                    tint = if (isPreviewingThis) CoralActive else CometSecondary,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }

                                                            IconButton(
                                                                onClick = { viewModel.clearRecording() },
                                                                modifier = Modifier
                                                                    .size(34.dp)
                                                                    .background(CoralActive.copy(alpha = 0.1f), CircleShape)
                                                            ) {
                                                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Видалити", tint = CoralActive, modifier = Modifier.size(16.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                2 -> { // Pick sound from device
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        if (pickedMediaUriString == null) {
                                            Button(
                                                onClick = { filePickerLauncher.launch("audio/*") },
                                                colors = ButtonDefaults.buttonColors(containerColor = CometPrimary),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(imageVector = Icons.Default.MusicNote, contentDescription = null)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Обрати файл з пристрою")
                                            }
                                        } else {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(CosmicNightSurfaceVariant, RoundedCornerShape(10.dp))
                                                    .padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(text = "Медичний файл:", color = TextSecondary, fontSize = 11.sp)
                                                    Text(
                                                        text = pickedMediaName ?: "Файл завантажено",
                                                        color = TextPrimary,
                                                        fontSize = 14.sp,
                                                        maxLines = 1
                                                    )
                                                }

                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    val isPreviewingThis = previewPlayingPath == pickedMediaUriString
                                                    IconButton(
                                                        onClick = {
                                                            if (isPreviewingThis) {
                                                                    onStopPreview()
                                                            } else {
                                                                pickedMediaUriString?.let { onPlayPreview(it) }
                                                            }
                                                        },
                                                        modifier = Modifier
                                                            .size(34.dp)
                                                            .background(CosmicNightBg, CircleShape)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isPreviewingThis) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                            contentDescription = "Слухати",
                                                            tint = if (isPreviewingThis) CoralActive else CometSecondary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }

                                                    IconButton(
                                                        onClick = {
                                                            pickedMediaUriString = null
                                                            pickedMediaName = null
                                                        },
                                                        modifier = Modifier
                                                            .size(34.dp)
                                                            .background(CoralActive.copy(alpha = 0.1f), CircleShape)
                                                    ) {
                                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Видалити", tint = CoralActive, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Alarm Label memo box
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CosmicNightSurface, RoundedCornerShape(16.dp))
                                .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Назва або замітка (необов'язково):",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = label,
                                onValueChange = { label = it },
                                placeholder = { Text("Напр., Тренування, Ліки", color = TextMuted) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CometPrimary,
                                    unfocusedBorderColor = BorderColor,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action controls at dialog bottom
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.linearGradient(listOf(BorderColor, BorderColor)))
                    ) {
                        Text("Скасувати", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val finalCustomPath = when (selectedSoundType) {
                                1 -> recordedPath
                                2 -> pickedMediaUriString
                                else -> null
                            }
                            if (selectedSoundType == 1 && finalCustomPath == null) {
                                Toast.makeText(context, "Рятуйте! Запишіть голос або оберіть інший звук", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            if (selectedSoundType == 2 && finalCustomPath == null) {
                                Toast.makeText(context, "Оберіть зовнішній файл", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            onSave(hour, minute, selectedSoundType, selectedBuiltInSound, finalCustomPath, selectedDays, label)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_alarm_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = CometAccent, contentColor = CosmicNightBg)
                    ) {
                        Text("Зберегти", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun MicrophonePulseAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale"
    )

    Box(
        modifier = Modifier
            .size(100.dp)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(pulseScale)
                .background(CoralActive.copy(alpha = 0.2f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(CoralActive, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = Icons.Default.Mic, contentDescription = null, tint = TextPrimary)
        }
    }
}

@Composable
fun FullScreenRingScreen(alarm: Alarm?, onDismiss: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "ring_transition")
    val scaleWave by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )

    val opacityWave by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "opacity"
    )

    val timeText = if (alarm != null) {
        String.format("%02d:%02d", alarm.hour, alarm.minute)
    } else {
        "Час!"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CosmicNightBg.copy(alpha = 0.98f))
            .clickable(enabled = false) {}, // Intercept touch events
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "ПОРА ВСТАВАТИ!",
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = CometSecondary,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = timeText,
                fontSize = 72.sp,
                fontWeight = FontWeight.ExtraBold,
                color = CometAccent
            )

            if (alarm?.label?.isNotEmpty() == true) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = alarm.label,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Sound playing ripple animation graphics
            Box(
                modifier = Modifier.size(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = CometPrimary.copy(alpha = opacityWave),
                        radius = size.minDimension / 2 * scaleWave,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(
                            Brush.linearGradient(listOf(CometPrimary, CometSecondary)),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Ringing",
                        tint = TextPrimary,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(64.dp))

            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CoralActive, contentColor = TextPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .testTag("dismiss_ring_button"),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
                Text(
                    text = "Вимкнути",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
