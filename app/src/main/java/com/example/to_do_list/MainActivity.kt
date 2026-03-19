package com.example.to_do_list // Kendi paket adını kontrol etmeyi unutma

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

// --- YARDIMCI FONKSİYON 1: Tarihi Metne Çevir ---
fun formatMillisToDateString(millis: Long?): String {
    if (millis == null) return "Tarih Yok"
    val formatter = SimpleDateFormat("dd MMM yyyy", Locale("tr"))
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date(millis))
}

// --- YARDIMCI FONKSİYON 2: Kalan Zamanı Hesapla ---
fun calculateRemainingTime(dueDateMillis: Long?, dueTimeString: String?): String? {
    if (dueDateMillis == null) return null

    val currentMillis = System.currentTimeMillis()
    val targetCalendar = Calendar.getInstance().apply { timeInMillis = dueDateMillis }

    if (dueTimeString != null) {
        val parts = dueTimeString.split(":")
        if (parts.size == 2) {
            targetCalendar.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
            targetCalendar.set(Calendar.MINUTE, parts[1].toInt())
            targetCalendar.add(Calendar.HOUR_OF_DAY, -3) // UTC düzeltmesi (Türkiye saati için)
        }
    } else {
        targetCalendar.set(Calendar.HOUR_OF_DAY, 23)
        targetCalendar.set(Calendar.MINUTE, 59)
    }

    val diff = targetCalendar.timeInMillis - currentMillis
    if (diff < 0) return "Süresi doldu!"

    val days = TimeUnit.MILLISECONDS.toDays(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff) % 24
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60

    return when {
        days > 0 -> "$days gün, $hours saat kaldı"
        hours > 0 -> "$hours saat, $minutes dk kaldı"
        else -> "$minutes dk kaldı"
    }
}

// --- YARDIMCI BİLEŞEN: Saat Seçici Diyalog ---
@Composable
fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxWidth(),
        title = { Text("Saat Seçin") },
        text = { content() },
        confirmButton = confirmButton,
        dismissButton = dismissButton
    )
}

// --- 1. VERİ MODELİ (Entity) ---
@Entity(tableName = "todo_table")
data class TodoItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val isDone: Boolean = false,
    val dueDate: Long? = null,
    val notes: String = "",
    val dueTime: String? = null, // YENİ: Saat
    val priority: Int? = null    // YENİ: Yıldız derecesi
)

// --- 2. VERİTABANI SORGULARI (DAO) ---
@Dao
interface TodoDao {
    // YENİ: Yapılmayanlar ve yüksek öncelikliler önce gelir
    @Query("SELECT * FROM todo_table ORDER BY isDone ASC, priority DESC, id DESC")
    fun getAll(): Flow<List<TodoItem>>

    @Insert
    suspend fun insert(item: TodoItem)

    @Delete
    suspend fun delete(item: TodoItem)

    @Update
    suspend fun update(item: TodoItem)
}

// --- 3. VERİTABANI KURULUMU ---
@Database(entities = [TodoItem::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
}

// --- 4. EKRAN DURUMLARI ---
enum class AppScreen { TASKS, CALENDAR, ADD_TASK }

// --- 5. ANA AKTİVİTE ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "todo-db").build()
        val dao = db.todoDao()

        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MainAppScaffold(dao)
                }
            }
        }
    }
}

// --- 6. ANA İSKELET VE YÖNLENDİRME ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(dao: TodoDao) {
    var currentScreen by remember { mutableStateOf(AppScreen.TASKS) }
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when(currentScreen) {
                            AppScreen.TASKS -> "QuickDo"
                            AppScreen.CALENDAR -> "Takvim"
                            AppScreen.ADD_TASK -> "Yeni Görev Ekle"
                        }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menü")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Görevlerim") },
                            onClick = { currentScreen = AppScreen.TASKS; menuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Takvim") },
                            onClick = { currentScreen = AppScreen.CALENDAR; menuExpanded = false }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (currentScreen == AppScreen.TASKS) {
                FloatingActionButton(onClick = { currentScreen = AppScreen.ADD_TASK }) {
                    Icon(Icons.Default.Add, contentDescription = "Görev Ekle")
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            when (currentScreen) {
                AppScreen.TASKS -> TaskListScreen(dao)
                AppScreen.CALENDAR -> CalendarViewScreen(dao)
                AppScreen.ADD_TASK -> AddTaskScreen(dao = dao, onNavigateBack = { currentScreen = AppScreen.TASKS })
            }
        }
    }
}

// --- 7. GÖREV EKLEME EKRANI (YENİLENMİŞ) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskScreen(dao: TodoDao, onNavigateBack: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }

    var selectedDate by remember { mutableStateOf<Long?>(null) }
    var selectedTime by remember { mutableStateOf<String?>(null) }
    var selectedPriority by remember { mutableStateOf<Int?>(null) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val datePickerState = rememberDatePickerState()
    val timePickerState = rememberTimePickerState(is24Hour = true)

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { selectedDate = datePickerState.selectedDateMillis; showDatePicker = false }) { Text("Tamam") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("İptal") } }
        ) { DatePicker(state = datePickerState) }
    }

    if (showTimePicker) {
        TimePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val hour = timePickerState.hour.toString().padStart(2, '0')
                    val minute = timePickerState.minute.toString().padStart(2, '0')
                    selectedTime = "$hour:$minute"
                    showTimePicker = false
                }) { Text("Tamam") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("İptal") } }
        ) { TimePicker(state = timePickerState, modifier = Modifier.fillMaxWidth()) }
    }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Ne yapılması gerekiyor?") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = notesText, onValueChange = { notesText = it }, label = { Text("Notlar (İsteğe bağlı)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Spacer(modifier = Modifier.height(16.dp))

        // Yıldız Seçimi
        Text("Önem Derecesi (İsteğe Bağlı):", style = MaterialTheme.typography.bodyMedium)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
            for (i in 1..5) {
                IconButton(onClick = { selectedPriority = if (selectedPriority == i) null else i }) {
                    Icon(
                        imageVector = if (selectedPriority != null && i <= selectedPriority!!) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = "$i Yıldız",
                        tint = if (selectedPriority != null && i <= selectedPriority!!) Color(0xFFFFC107) else Color.Gray,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tarih ve Saat Seçimi
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.DateRange, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (selectedDate == null) "Tarih Ekle" else formatMillisToDateString(selectedDate))
            }
            OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.weight(1f)) {
                Text(selectedTime ?: "Saat Ekle")
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onNavigateBack) { Text("İptal Et") }
            Button(onClick = {
                if (text.isNotBlank()) {
                    scope.launch {
                        dao.insert(TodoItem(title = text, dueDate = selectedDate, dueTime = selectedTime, priority = selectedPriority, notes = notesText))
                        onNavigateBack()
                    }
                }
            }) { Text("Görevi Kaydet") }
        }
    }
}

// --- 8. GÖREV LİSTESİ EKRANI ---
@Composable
fun TaskListScreen(dao: TodoDao) {
    val items by dao.getAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Görev yok. Sağ alttaki + butonuna tıkla!", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            items(items, key = { it.id }) { item ->
                TodoItemRow(
                    item = item,
                    onCheckedChange = { isChecked -> scope.launch { dao.update(item.copy(isDone = isChecked)) } },
                    onDeleteClick = { scope.launch { dao.delete(item) } },
                    onTaskUpdate = { updatedItem -> scope.launch { dao.update(updatedItem) } }
                )
            }
        }
    }
}

// --- 9. TAKVİM EKRANI ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarViewScreen(dao: TodoDao) {
    val items by dao.getAll().collectAsState(initial = emptyList())
    val datePickerState = rememberDatePickerState()
    val scope = rememberCoroutineScope()

    val upcomingTasks = items.filter { it.dueDate != null }.sortedBy { it.dueDate }
    val selectedDateString = formatMillisToDateString(datePickerState.selectedDateMillis)
    val tasksForSelectedDate = items.filter { item ->
        item.dueDate != null && formatMillisToDateString(item.dueDate) == selectedDateString
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
        item {
            DatePicker(state = datePickerState, modifier = Modifier.fillMaxWidth(), title = null, headline = null, showModeToggle = false)
        }

        if (upcomingTasks.isNotEmpty()) {
            item {
                Text(
                    text = "Hızlı Erişim: Tarihli Görevler",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(upcomingTasks) { task ->
                        Card(
                            modifier = Modifier.clickable { datePickerState.selectedDateMillis = task.dueDate },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("📅", modifier = Modifier.padding(end = 4.dp))
                                Text(text = task.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 16.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        item {
            Text(
                text = if (datePickerState.selectedDateMillis == null) "Tarih Seçin" else "$selectedDateString Görevleri",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                style = MaterialTheme.typography.titleMedium
            )
        }

        items(tasksForSelectedDate, key = { it.id }) { item ->
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                TodoItemRow(
                    item = item,
                    onCheckedChange = { isChecked -> scope.launch { dao.update(item.copy(isDone = isChecked)) } },
                    onDeleteClick = { scope.launch { dao.delete(item) } },
                    onTaskUpdate = { updatedItem -> scope.launch { dao.update(updatedItem) } }
                )
            }
        }
    }
}

// --- 10. GÖREV KARTI (YENİLENMİŞ) ---
@Composable
fun TodoItemRow(
    item: TodoItem,
    onCheckedChange: (Boolean) -> Unit,
    onDeleteClick: () -> Unit,
    onTaskUpdate: (TodoItem) -> Unit
) {
    var expanded by remember(item.id) { mutableStateOf(false) }
    var currentNotes by remember(item.id, item.notes) { mutableStateOf(item.notes) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { expanded = !expanded }.animateContentSize(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (expanded) 6.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = item.isDone, onCheckedChange = onCheckedChange)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        textDecoration = if (item.isDone) TextDecoration.LineThrough else TextDecoration.None
                    )

                    // Alt Bilgi: Tarih, Saat ve Kalan Zaman
                    if (item.dueDate != null) {
                        val remainingTimeText = calculateRemainingTime(item.dueDate, item.dueTime)
                        val timeStr = if (item.dueTime != null) " - ${item.dueTime}" else ""

                        Text(
                            text = "📅 ${formatMillisToDateString(item.dueDate)}$timeStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!item.isDone && remainingTimeText != null) {
                            Text(
                                text = "⏳ $remainingTimeText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // Yıldız Gösterimi
                if (item.priority != null) {
                    Row {
                        for (i in 1..item.priority) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error)
                }
            }

            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                OutlinedTextField(
                    value = currentNotes,
                    onValueChange = { currentNotes = it },
                    label = { Text("Görev Detayları / Notlar") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        onTaskUpdate(item.copy(notes = currentNotes))
                        expanded = false
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Notu Kaydet")
                }
            }
        }
    }
}