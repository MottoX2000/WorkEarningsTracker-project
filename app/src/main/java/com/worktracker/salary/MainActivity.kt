package com.worktracker.salary

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

const val KEY_RUNNING_START = "running_start_ms"

data class WorkSession(
    val id: String,
    val dateIso: String,
    val durationSec: Long
)

data class AppSettings(
    val hourlyRate: Double = 25.0,
    val currency: String = "$",
    val dailyGoalHours: Double = 8.0,
    val monthlyGoalHours: Double = 160.0
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(
                    colorScheme = lightColorScheme(
                        primary = Color(0xFF10B981),
                        secondary = Color(0xFFF59E0B),
                        background = Color(0xFFF0FDF4),
                        surface = Color.White,
                        onPrimary = Color.White,
                        onBackground = Color(0xFF1E293B),
                        onSurface = Color(0xFF1E293B)
                    )
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        WorkTrackerLightApp()
                    }
                }
            }
        }
    }
}

@Composable
fun WorkTrackerLightApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("work_tracker_light_prefs", Context.MODE_PRIVATE) }

    var settings by remember { mutableStateOf(loadSettings(prefs)) }
    var sessions by remember { mutableStateOf(loadSessions(prefs)) }

    // استمرار العداد: نقرأ وقت البدء المحفوظ (إن وجد) ليكمل العداد بعد إغلاق التطبيق
    val savedStartMs = remember { prefs.getLong(KEY_RUNNING_START, 0L) }
    var isRunning by remember { mutableStateOf(savedStartMs > 0L) }
    var startTimeMs by remember { mutableStateOf(savedStartMs) }
    var liveElapsedMs by remember {
        mutableStateOf(if (savedStartMs > 0L) max(0L, System.currentTimeMillis() - savedStartMs) else 0L)
    }
    var selectedTab by remember { mutableStateOf(0) }
    var showManualDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isRunning, startTimeMs) {
        while (isRunning) {
            liveElapsedMs = max(0L, System.currentTimeMillis() - startTimeMs)
            delay(100L)
        }
    }

    val todayIso = LocalDate.now().toString()
    val currentMonthPrefix = todayIso.substring(0, 7)
    val activeSec = liveElapsedMs / 1000.0
    val ratePerSec = settings.hourlyRate / 3600.0
    val sessionEarnings = activeSec * ratePerSec

    // ====================================================================
    // FOCUS MODE: When counting time, ONLY show Money + Time + Exit Button
    // ====================================================================
    if (isRunning) {
        FocusRunningScreen(
            moneyEarned = sessionEarnings,
            currency = settings.currency,
            elapsedMs = liveElapsedMs,
            onExit = {
                val sec = max(0L, System.currentTimeMillis() - startTimeMs) / 1000L
                isRunning = false
                liveElapsedMs = 0L
                prefs.edit().putLong(KEY_RUNNING_START, 0L).apply()
                if (sec >= 1L) {
                    val updated = sessions + WorkSession(
                        id = "s_${System.currentTimeMillis()}",
                        dateIso = todayIso,
                        durationSec = sec
                    )
                    sessions = updated
                    saveSessions(prefs, updated)
                }
            }
        )
        return
    }

    val todaySavedSec = sessions.filter { it.dateIso == todayIso }.sumOf { it.durationSec }
    val monthSavedSec = sessions.filter { it.dateIso.startsWith(currentMonthPrefix) }.sumOf { it.durationSec }

    val todayEarnings = todaySavedSec * ratePerSec
    val monthEarnings = monthSavedSec * ratePerSec

    Scaffold(
        containerColor = Color(0xFFF0FDF4),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ساعاتي وأرباحي", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color(0xFF065F46))
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFEF3C7)
                ) {
                    Text(
                        text = String.format(Locale.US, "%.0f %s / س", settings.hourlyRate, settings.currency),
                        color = Color(0xFFB45309),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Timer, contentDescription = null) },
                    label = { Text("العداد") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                    label = { Text("الشهر") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("الإعدادات") }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> SimpleHomeTab(
                    settings = settings,
                    todaySec = todaySavedSec,
                    todayEarnings = todayEarnings,
                    monthSec = monthSavedSec,
                    monthEarnings = monthEarnings,
                    onStart = {
                        startTimeMs = System.currentTimeMillis()
                        liveElapsedMs = 0L
                        isRunning = true
                        prefs.edit().putLong(KEY_RUNNING_START, startTimeMs).apply()
                    },
                    onOpenManual = { showManualDialog = true }
                )
                1 -> SimpleMonthTab(settings = settings, sessions = sessions)
                2 -> SimpleSettingsTab(
                    settings = settings,
                    onSave = {
                        settings = it
                        saveSettings(prefs, it)
                    }
                )
            }
        }
    }

    if (showManualDialog) {
        SimpleManualDialog(
            onDismiss = { showManualDialog = false },
            onSave = { hours, mins ->
                val sec = hours * 3600L + mins * 60L
                if (sec > 0L) {
                    val updated = sessions + WorkSession("m_${System.currentTimeMillis()}", todayIso, sec)
                    sessions = updated
                    saveSessions(prefs, updated)
                }
                showManualDialog = false
            }
        )
    }
}

@Composable
fun FocusRunningScreen(
    moneyEarned: Double,
    currency: String,
    elapsedMs: Long,
    onExit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFECFDF5), Color.White, Color(0xFFFFFBEB))
                )
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Center Circle: ONLY Money Counter & Time Counter
        Surface(
            modifier = Modifier.size(300.dp),
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 1. Money Counter
                Text(
                    text = String.format(Locale.US, "%.4f %s", moneyEarned, currency),
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF059669)
                )
                Spacer(modifier = Modifier.height(14.dp))
                // 2. Time Counter
                Text(
                    text = formatStopwatchMs(elapsedMs),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF64748B)
                )
            }
        }

        // 3. Single Exit Button
        Button(
            onClick = onExit,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF43F5E)),
            shape = CircleShape,
            modifier = Modifier
                .height(56.dp)
                .width(180.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("خروج", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
fun SimpleHomeTab(
    settings: AppSettings,
    todaySec: Long,
    todayEarnings: Double,
    monthSec: Long,
    monthEarnings: Double,
    onStart: () -> Unit,
    onOpenManual: () -> Unit
) {
    val dailyProgress = min(1f, (todaySec / (settings.dailyGoalHours * 3600.0)).toFloat())
    val monthlyProgress = min(1f, (monthSec / (settings.monthlyGoalHours * 3600.0)).toFloat())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("0.00 ${settings.currency}", fontSize = 36.sp, fontWeight = FontWeight.Black, color = Color(0xFF10B981))
                Text("00:00:00", fontSize = 20.sp, color = Color(0xFF94A3B8), fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onStart,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("بدء العمل", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("☀️ اليوم", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF64748B))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        String.format(Locale.US, "%.2f %s", todayEarnings, settings.currency),
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        color = Color(0xFF10B981)
                    )
                    Text(formatDurationArabic(todaySec), fontSize = 12.sp, color = Color(0xFF64748B))
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { dailyProgress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = Color(0xFF10B981),
                        trackColor = Color(0xFFECFDF5)
                    )
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📅 الشهر", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF64748B))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        String.format(Locale.US, "%.2f %s", monthEarnings, settings.currency),
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        color = Color(0xFFF59E0B)
                    )
                    Text(formatDurationArabic(monthSec), fontSize = 12.sp, color = Color(0xFF64748B))
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { monthlyProgress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = Color(0xFFF59E0B),
                        trackColor = Color(0xFFFFFBEB)
                    )
                }
            }
        }

        OutlinedButton(onClick = onOpenManual, shape = CircleShape) {
            Text("+ إضافة ساعات يدوياً", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SimpleMonthTab(settings: AppSettings, sessions: List<WorkSession>) {
    val currentYm = YearMonth.now().toString()
    val grouped = sessions.filter { it.dateIso.startsWith(currentYm) }
        .groupBy { it.dateIso }
        .toSortedMap(compareByDescending { it })

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(grouped.entries.toList(), key = { it.key }) { (dateIso, list) ->
            val sec = list.sumOf { it.durationSec }
            val earn = (sec / 3600.0) * settings.hourlyRate
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(dateIso, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        Text(formatDurationArabic(sec), fontSize = 12.sp, color = Color(0xFF64748B))
                    }
                    Text(
                        String.format(Locale.US, "%.2f %s", earn, settings.currency),
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = Color(0xFF10B981)
                    )
                }
            }
        }
    }
}

@Composable
fun SimpleSettingsTab(settings: AppSettings, onSave: (AppSettings) -> Unit) {
    var rate by remember(settings) { mutableStateOf(settings.hourlyRate.toString()) }
    var curr by remember(settings) { mutableStateOf(settings.currency) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = rate,
            onValueChange = { rate = it },
            label = { Text("سعر الساعة") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = curr,
            onValueChange = { curr = it },
            label = { Text("العملة") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                onSave(settings.copy(hourlyRate = rate.toDoubleOrNull() ?: settings.hourlyRate, currency = curr.ifBlank { "$" }))
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = CircleShape
        ) {
            Text("حفظ", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SimpleManualDialog(onDismiss: () -> Unit, onSave: (Long, Long) -> Unit) {
    var h by remember { mutableStateOf("1") }
    var m by remember { mutableStateOf("30") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة جلسة") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = h, onValueChange = { h = it }, label = { Text("ساعات") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = m, onValueChange = { m = it }, label = { Text("دقائق") }, modifier = Modifier.weight(1f))
            }
        },
        confirmButton = {
            Button(onClick = { onSave(h.toLongOrNull() ?: 0L, m.toLongOrNull() ?: 0L) }) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

fun formatStopwatchMs(ms: Long): String {
    val totalTenths = ms / 100L
    val tenths = totalTenths % 10L
    val totalSeconds = totalTenths / 10L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d:%02d.%d", hours, minutes, seconds, tenths)
}

fun formatDurationArabic(seconds: Long): String {
    val totalMins = (seconds + 30L) / 60L
    val hrs = totalMins / 60L
    val mins = totalMins % 60L
    return when {
        hrs == 0L -> "${mins} د"
        mins == 0L -> "${hrs} س"
        else -> "${hrs} س ${mins} د"
    }
}

fun loadSettings(prefs: android.content.SharedPreferences): AppSettings {
    return AppSettings(
        hourlyRate = prefs.getFloat("hourly_rate", 25f).toDouble(),
        currency = prefs.getString("currency", "$") ?: "$",
        dailyGoalHours = prefs.getFloat("daily_goal", 8f).toDouble(),
        monthlyGoalHours = prefs.getFloat("monthly_goal", 160f).toDouble()
    )
}

fun saveSettings(prefs: android.content.SharedPreferences, s: AppSettings) {
    prefs.edit()
        .putFloat("hourly_rate", s.hourlyRate.toFloat())
        .putString("currency", s.currency)
        .putFloat("daily_goal", s.dailyGoalHours.toFloat())
        .putFloat("monthly_goal", s.monthlyGoalHours.toFloat())
        .apply()
}

fun loadSessions(prefs: android.content.SharedPreferences): List<WorkSession> {
    val raw = prefs.getString("sessions_json", null) ?: return emptyList()
    return try {
        val arr = JSONArray(raw)
        val list = mutableListOf<WorkSession>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(WorkSession(o.optString("id"), o.optString("dateIso"), o.optLong("durationSec")))
        }
        list
    } catch (e: Exception) {
        emptyList()
    }
}

fun saveSessions(prefs: android.content.SharedPreferences, list: List<WorkSession>) {
    val arr = JSONArray()
    list.forEach { s ->
        val o = JSONObject()
        o.put("id", s.id)
        o.put("dateIso", s.dateIso)
        o.put("durationSec", s.durationSec)
        arr.put(o)
    }
    prefs.edit().putString("sessions_json", arr.toString()).apply()
}
