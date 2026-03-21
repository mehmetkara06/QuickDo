package com.example.to_do_list // Kendi paket adını kontrol et!

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import androidx.glance.appwidget.updateAll // Widget'ı güncellemek için gerekli

// --- YARDIMCI FONKSİYON 1: Tarihi Formatla ---
fun formatMillisToDateString(millis: Long?): String {
    if (millis == null) return "Her Gün"
    val formatter = SimpleDateFormat("dd MMM yyyy", Locale("tr"))
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date(millis))
}

// --- YARDIMCI FONKSİYON 2: KALAN ZAMAN ---
fun calculateRemainingTime(dueDateMillis: Long?, dueTimeString: String?): String? {
    if (dueDateMillis == null && dueTimeString == null) return null
    val currentMillis = System.currentTimeMillis()
    val targetCalendar = Calendar.getInstance()

    if (dueDateMillis != null) {
        targetCalendar.timeInMillis = dueDateMillis
        if (dueTimeString != null) {
            val parts = dueTimeString.split(":")
            if (parts.size == 2) {
                targetCalendar.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
                targetCalendar.set(Calendar.MINUTE, parts[1].toInt())
                targetCalendar.set(Calendar.SECOND, 0)
            }
        } else {
            targetCalendar.set(Calendar.HOUR_OF_DAY, 23); targetCalendar.set(Calendar.MINUTE, 59); targetCalendar.set(Calendar.SECOND, 59)
        }
    } else if (dueTimeString != null) {
        val parts = dueTimeString.split(":")
        if (parts.size == 2) {
            targetCalendar.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
            targetCalendar.set(Calendar.MINUTE, parts[1].toInt())
            targetCalendar.set(Calendar.SECOND, 0)
            if (targetCalendar.timeInMillis <= currentMillis) {
                targetCalendar.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
    }

    val diff = targetCalendar.timeInMillis - currentMillis
    if (diff < 0 && dueDateMillis != null) return "Süresi doldu!"

    val days = TimeUnit.MILLISECONDS.toDays(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff) % 24
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60

    return when {
        days > 0 -> "$days gün, $hours saat kaldı"
        hours > 0 -> "$hours saat, $minutes dk kaldı"
        else -> "$minutes dk kaldı"
    }
}

// --- YARDIMCI FONKSİYON 3: ALARM KURUCU ---
fun scheduleNotification(context: Context, taskId: Int, title: String, notes: String, dueDateMillis: Long?, dueTimeString: String?, priority: Int?) {
    if (dueDateMillis == null && dueTimeString == null) return
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val isRecurring = (dueDateMillis == null && dueTimeString != null)

    val intent = Intent(context, NotificationReceiver::class.java).apply {
        putExtra("taskId", taskId)
        putExtra("title", title)
        putExtra("message", notes.ifBlank { "Zamanı geldi!" })
        putExtra("priority", priority ?: 3)
        putExtra("isRecurring", isRecurring)
        putExtra("dueTimeString", dueTimeString)
    }
    val pendingIntent = PendingIntent.getBroadcast(context, taskId, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    val targetCalendar = Calendar.getInstance()

    if (isRecurring && dueTimeString != null) {
        val parts = dueTimeString.split(":")
        if (parts.size == 2) {
            targetCalendar.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
            targetCalendar.set(Calendar.MINUTE, parts[1].toInt())
            targetCalendar.set(Calendar.SECOND, 0)
            if (targetCalendar.timeInMillis <= System.currentTimeMillis()) {
                targetCalendar.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
    } else if (dueDateMillis != null) {
        targetCalendar.timeInMillis = dueDateMillis
        if (dueTimeString != null) {
            val parts = dueTimeString.split(":")
            if (parts.size == 2) { targetCalendar.set(Calendar.HOUR_OF_DAY, parts[0].toInt()); targetCalendar.set(Calendar.MINUTE, parts[1].toInt()); targetCalendar.set(Calendar.SECOND, 0) }
        } else {
            targetCalendar.set(Calendar.HOUR_OF_DAY, 9); targetCalendar.set(Calendar.MINUTE, 0); targetCalendar.set(Calendar.SECOND, 0)
        }
    }

    if (!isRecurring && targetCalendar.timeInMillis <= System.currentTimeMillis()) return

    try {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetCalendar.timeInMillis, pendingIntent)
    } catch (e: SecurityException) {
        alarmManager.set(AlarmManager.RTC_WAKEUP, targetCalendar.timeInMillis, pendingIntent)
    }
}

// YARDIMCI FONKSİYON 4: ALARMI İPTAL ET
fun cancelNotification(context: Context, taskId: Int) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, NotificationReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        taskId,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    alarmManager.cancel(pendingIntent)
}

// YENİ YARDIMCI FONKSİYON 5: WIDGET'I ANINDA YENİLE (Gecikme Korumalı)
fun refreshQuickDoWidget(context: Context) {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        // ÇÖZÜM BURADA: Veritabanının kaydı tam olarak bitirmesi için çeyrek saniye bekle!
        kotlinx.coroutines.delay(300)
        QuickDoWidget().updateAll(context)
    }
}

@Composable
fun TimePickerDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit, dismissButton: @Composable () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(onDismissRequest = onDismissRequest, modifier = Modifier.fillMaxWidth(), title = { Text("Saat Seçin") }, text = { content() }, confirmButton = confirmButton, dismissButton = dismissButton)
}

// --- 1. VERİ MODELLERİ ---
@Entity(tableName = "category_table")
data class Category(@PrimaryKey(autoGenerate = true) val id: Int = 0, val name: String, val colorCode: Long)

@Entity(tableName = "todo_table")
data class TodoItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0, val title: String, val isDone: Boolean = false, val dueDate: Long? = null,
    val notes: String = "", val dueTime: String? = null, val priority: Int? = null, val categoryId: Int? = null,
    val hasReminder: Boolean = false
)

// --- 2. VERİTABANI SORGULARI ---
@Dao
interface TodoDao {
    @Query("SELECT * FROM todo_table ORDER BY isDone ASC, priority DESC, id DESC")
    fun getAll(): Flow<List<TodoItem>>

    @Insert suspend fun insert(item: TodoItem): Long
    @Delete suspend fun delete(item: TodoItem)
    @Update suspend fun update(item: TodoItem)

    @Query("SELECT * FROM category_table")
    fun getAllCategories(): Flow<List<Category>>
    @Insert suspend fun insertCategory(category: Category)
    @Query("SELECT COUNT(*) FROM category_table")
    suspend fun getCategoryCount(): Int
}

@Database(entities = [TodoItem::class, Category::class], version = 1)
abstract class AppDatabase : RoomDatabase() { abstract fun todoDao(): TodoDao }

enum class AppScreen { TASKS, CALENDAR, ADD_TASK }
enum class SortType(val label: String) { DEFAULT("Varsayılan Sıralama"), DATE_ASC("Önce Yakın Tarihliler"), DATE_DESC("Önce Uzak Tarihliler"), PRIORITY("Önce En Önemliler") }

// --- 3. ANA AKTİVİTE ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "todo-db").build()
        val dao = db.todoDao()
        setContent { MaterialTheme { Surface(color = MaterialTheme.colorScheme.background) { MainAppScaffold(dao) } } }
    }
}

// --- 4. ANA İSKELET VE NAVİGASYON ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(dao: TodoDao) {
    var currentScreen by remember { mutableStateOf(AppScreen.TASKS) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val categories by dao.getAllCategories().collectAsState(initial = emptyList())
    val allTasks by dao.getAll().collectAsState(initial = emptyList())
    var showFilterDialog by remember { mutableStateOf(false) }
    var currentSortType by remember { mutableStateOf(SortType.DEFAULT) }
    var currentCategoryFilter by remember { mutableStateOf<Category?>(null) }

    LaunchedEffect(Unit) {
        if (dao.getCategoryCount() == 0) {
            dao.insertCategory(Category(name = "💊 İlaç Hatırlatıcısı", colorCode = 0xFFE53935))
            dao.insertCategory(Category(name = "🎂 Doğum Günü", colorCode = 0xFFE91E63))
            dao.insertCategory(Category(name = "📚 Sınav Takvimi", colorCode = 0xFF2196F3))
        }
    }

    if (showFilterDialog) {
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            title = { Text("Filtrele ve Sırala") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Sıralama Ölçütü", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    SortType.values().forEach { sortType ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { currentSortType = sortType }.padding(vertical = 4.dp)) { RadioButton(selected = currentSortType == sortType, onClick = { currentSortType = sortType }); Text(text = sortType.label, modifier = Modifier.padding(start = 8.dp)) }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Kategori Filtresi", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { currentCategoryFilter = null }.padding(vertical = 4.dp)) { RadioButton(selected = currentCategoryFilter == null, onClick = { currentCategoryFilter = null }); Text("Tüm Kategoriler", modifier = Modifier.padding(start = 8.dp)) }
                    categories.forEach { cat ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { currentCategoryFilter = cat }.padding(vertical = 4.dp)) { RadioButton(selected = currentCategoryFilter == cat, onClick = { currentCategoryFilter = cat }); Box(modifier = Modifier.padding(start = 8.dp).size(12.dp).background(Color(cat.colorCode), CircleShape)); Text(cat.name, modifier = Modifier.padding(start = 8.dp)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFilterDialog = false }) { Text("Uygula") } }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(24.dp))
                Text("QuickDo", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                HorizontalDivider()
                NavigationDrawerItem(label = { Text("📋 Görevlerim") }, selected = currentScreen == AppScreen.TASKS, onClick = { currentScreen = AppScreen.TASKS; scope.launch { drawerState.close() } }, modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding))
                NavigationDrawerItem(label = { Text("📅 Takvim") }, selected = currentScreen == AppScreen.CALENDAR, onClick = { currentScreen = AppScreen.CALENDAR; scope.launch { drawerState.close() } }, modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding))
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(when(currentScreen) { AppScreen.TASKS -> "Görevlerim"; AppScreen.CALENDAR -> "Takvim"; AppScreen.ADD_TASK -> "Yeni Görev" }) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, "Menüyü Aç") } },
                    actions = { if (currentScreen == AppScreen.TASKS) { IconButton(onClick = { showFilterDialog = true }) { Text("🔽", style = MaterialTheme.typography.titleLarge) } } }
                )
            },
            floatingActionButton = { if (currentScreen == AppScreen.TASKS) { FloatingActionButton(onClick = { currentScreen = AppScreen.ADD_TASK }) { Icon(Icons.Default.Add, "Görev Ekle") } } }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                when (currentScreen) {
                    AppScreen.TASKS -> TaskListScreen(dao, allTasks, categories, currentSortType, currentCategoryFilter)
                    AppScreen.CALENDAR -> CalendarViewScreen(dao, allTasks, categories)
                    AppScreen.ADD_TASK -> AddTaskScreen(dao = dao, onNavigateBack = { currentScreen = AppScreen.TASKS })
                }
            }
        }
    }
}

// --- 5. GÖREV EKLEME EKRANI ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskScreen(dao: TodoDao, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf<Long?>(null) }
    var selectedTime by remember { mutableStateOf<String?>(null) }
    var selectedPriority by remember { mutableStateOf<Int?>(null) }
    var hasReminder by remember { mutableStateOf(false) }

    val categories by dao.getAllCategories().collectAsState(initial = emptyList())
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var expandedCategoryMenu by remember { mutableStateOf(false) }
    var showNewCategoryDialog by remember { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val datePickerState = rememberDatePickerState()
    val timePickerState = rememberTimePickerState(is24Hour = true)

    if (showNewCategoryDialog) {
        var newCatName by remember { mutableStateOf("") }
        val colorPalette = listOf(0xFFE53935, 0xFFE91E63, 0xFF9C27B0, 0xFF3F51B5, 0xFF2196F3, 0xFF009688, 0xFF4CAF50, 0xFFFF9800, 0xFF795548)
        var selectedColorCode by remember { mutableStateOf(colorPalette[0]) }

        AlertDialog(
            onDismissRequest = { showNewCategoryDialog = false }, title = { Text("Yeni Kategori") },
            text = {
                Column {
                    OutlinedTextField(value = newCatName, onValueChange = { newCatName = it }, label = { Text("Kategori Adı") }, singleLine = true)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Renk Seçin:", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(colorPalette) { colorCode -> Box(modifier = Modifier.size(36.dp).background(Color(colorCode), CircleShape).border(if (selectedColorCode == colorCode) 3.dp else 0.dp, Color.Black.copy(alpha = 0.5f), CircleShape).clickable { selectedColorCode = colorCode }) } }
                }
            },
            confirmButton = { TextButton(onClick = { if (newCatName.isNotBlank()) { scope.launch { dao.insertCategory(Category(name = newCatName, colorCode = selectedColorCode)); showNewCategoryDialog = false } } }) { Text("Oluştur") } }, dismissButton = { TextButton(onClick = { showNewCategoryDialog = false }) { Text("İptal") } }
        )
    }

    if (showDatePicker) { DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton(onClick = { selectedDate = datePickerState.selectedDateMillis; showDatePicker = false }) { Text("Tamam") } }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("İptal") } }) { DatePicker(state = datePickerState) } }
    if (showTimePicker) { TimePickerDialog(onDismissRequest = { showTimePicker = false }, confirmButton = { TextButton(onClick = { val hour = timePickerState.hour.toString().padStart(2, '0'); val minute = timePickerState.minute.toString().padStart(2, '0'); selectedTime = "$hour:$minute"; showTimePicker = false }) { Text("Tamam") } }, dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("İptal") } }) { TimePicker(state = timePickerState, modifier = Modifier.fillMaxWidth()) } }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Ne yapılması gerekiyor?") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))

        ExposedDropdownMenuBox(expanded = expandedCategoryMenu, onExpandedChange = { expandedCategoryMenu = !expandedCategoryMenu }) {
            OutlinedTextField(value = selectedCategory?.name ?: "Kategori Seç (İsteğe Bağlı)", onValueChange = {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategoryMenu) }, colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(), modifier = Modifier.menuAnchor().fillMaxWidth())
            ExposedDropdownMenu(expanded = expandedCategoryMenu, onDismissRequest = { expandedCategoryMenu = false }) {
                categories.forEach { cat -> DropdownMenuItem(text = { Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(12.dp).background(Color(cat.colorCode), CircleShape)); Spacer(modifier = Modifier.width(8.dp)); Text(cat.name) } }, onClick = { selectedCategory = cat; expandedCategoryMenu = false }) }
                Divider()
                DropdownMenuItem(text = { Text("➕ Yeni Kategori Oluştur", color = MaterialTheme.colorScheme.primary) }, onClick = { expandedCategoryMenu = false; showNewCategoryDialog = true })
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = notesText, onValueChange = { notesText = it }, label = { Text("Notlar (İsteğe bağlı)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Spacer(modifier = Modifier.height(16.dp))

        Text("Önem Derecesi (İsteğe Bağlı):", style = MaterialTheme.typography.bodyMedium)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
            for (i in 1..5) { IconButton(onClick = { selectedPriority = if (selectedPriority == i) null else i }) { Icon(imageVector = if (selectedPriority != null && i <= selectedPriority!!) Icons.Filled.Star else Icons.Outlined.Star, contentDescription = "$i Yıldız", tint = if (selectedPriority != null && i <= selectedPriority!!) Color(0xFFFFC107) else Color.Gray, modifier = Modifier.size(36.dp)) } }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.DateRange, contentDescription = null); Spacer(modifier = Modifier.width(4.dp)); Text(if (selectedDate == null) "Tarih Ekle" else formatMillisToDateString(selectedDate)) }
            OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.weight(1f)) { Text(selectedTime ?: "Saat Ekle") }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = if (hasReminder) MaterialTheme.colorScheme.primary else Color.Gray)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Bana Hatırlat (Alarm Kur)", style = MaterialTheme.typography.bodyLarge)
            }
            Switch(checked = hasReminder, onCheckedChange = { hasReminder = it })
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onNavigateBack) { Text("İptal Et") }
            Button(onClick = {
                if (text.isNotBlank()) {
                    scope.launch {
                        val newItem = TodoItem(title = text, dueDate = selectedDate, dueTime = selectedTime, priority = selectedPriority, notes = notesText, categoryId = selectedCategory?.id, hasReminder = hasReminder)
                        val insertedId = dao.insert(newItem).toInt()

                        if (hasReminder && (selectedDate != null || selectedTime != null)) {
                            scheduleNotification(context, insertedId, text, notesText, selectedDate, selectedTime, selectedPriority)
                        }

                        // YENİ EKLENEN: Yeni görev eklendiğinde de widget yenilensin!
                        refreshQuickDoWidget(context)

                        onNavigateBack()
                    }
                }
            }) { Text("Görevi Kaydet") }
        }
    }
}

// --- 6. GÖREV LİSTESİ EKRANI ---
@Composable
fun TaskListScreen(dao: TodoDao, allTasks: List<TodoItem>, categories: List<Category>, sortType: SortType, filterCategory: Category?) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current // İptal ve Widget için Context eklendi

    var displayItems = if (filterCategory == null) allTasks else allTasks.filter { it.categoryId == filterCategory.id }
    displayItems = when(sortType) { SortType.DEFAULT -> displayItems; SortType.DATE_ASC -> displayItems.sortedWith(compareBy({ it.isDone }, { it.dueDate ?: Long.MAX_VALUE })); SortType.DATE_DESC -> displayItems.sortedWith(compareBy({ it.isDone }, { -(it.dueDate ?: 0L) })); SortType.PRIORITY -> displayItems.sortedWith(compareBy({ it.isDone }, { -(it.priority ?: 0) })) }

    if (displayItems.isEmpty()) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(if (filterCategory == null) "Görev yok. Sağ alttaki + butonuna tıkla!" else "Bu kriterlere uygun görev bulunamadı.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    } else { LazyColumn(contentPadding = PaddingValues(16.dp)) { items(displayItems, key = { it.id }) { item ->
        TodoItemRow(
            item = item,
            categories = categories,
            onCheckedChange = { isChecked ->
                scope.launch {
                    dao.update(item.copy(isDone = isChecked))
                    if (isChecked) cancelNotification(context, item.id)
                    else if (item.hasReminder) scheduleNotification(context, item.id, item.title, item.notes, item.dueDate, item.dueTime, item.priority)

                    // YENİ EKLENEN: Görev durumu değiştiğinde widget yenilenir
                    refreshQuickDoWidget(context)
                }
            },
            onDeleteClick = {
                scope.launch {
                    dao.delete(item)
                    cancelNotification(context, item.id)

                    // YENİ EKLENEN: Görev silindiğinde widget yenilenir
                    refreshQuickDoWidget(context)
                }
            },
            onTaskUpdate = { updatedItem -> scope.launch { dao.update(updatedItem) } }
        ) } } }
}

// --- 7. TAKVİM EKRANI ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarViewScreen(dao: TodoDao, allTasks: List<TodoItem>, categories: List<Category>) {
    val datePickerState = rememberDatePickerState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current // İptal ve Widget için Context eklendi

    val upcomingTasks = allTasks.filter { it.dueDate != null }.sortedBy { it.dueDate }
    val selectedDateString = formatMillisToDateString(datePickerState.selectedDateMillis)
    val tasksForSelectedDate = allTasks.filter { item -> item.dueDate != null && formatMillisToDateString(item.dueDate) == selectedDateString }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
        item { DatePicker(state = datePickerState, modifier = Modifier.fillMaxWidth(), title = null, headline = null, showModeToggle = false) }
        if (upcomingTasks.isNotEmpty()) {
            item { Text("Hızlı Erişim: Tarihli Görevler", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(upcomingTasks) { task -> Card(modifier = Modifier.clickable { datePickerState.selectedDateMillis = task.dueDate }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) { Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text("📅", modifier = Modifier.padding(end = 4.dp)); Text(text = task.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer) } } } }
                HorizontalDivider(modifier = Modifier.padding(top = 16.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        item { Text(text = if (datePickerState.selectedDateMillis == null) "Tarih Seçin" else "$selectedDateString Görevleri", modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp), style = MaterialTheme.typography.titleMedium) }
        items(tasksForSelectedDate, key = { it.id }) { item -> Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            TodoItemRow(
                item = item,
                categories = categories,
                onCheckedChange = { isChecked ->
                    scope.launch {
                        dao.update(item.copy(isDone = isChecked))
                        if (isChecked) cancelNotification(context, item.id)
                        else if (item.hasReminder) scheduleNotification(context, item.id, item.title, item.notes, item.dueDate, item.dueTime, item.priority)

                        // YENİ EKLENEN: Görev durumu değiştiğinde widget yenilenir
                        refreshQuickDoWidget(context)
                    }
                },
                onDeleteClick = {
                    scope.launch {
                        dao.delete(item)
                        cancelNotification(context, item.id)

                        // YENİ EKLENEN: Görev silindiğinde widget yenilenir
                        refreshQuickDoWidget(context)
                    }
                },
                onTaskUpdate = { updatedItem -> scope.launch { dao.update(updatedItem) } }
            ) } }
    }
}

// --- 8. GÖREV KARTI (SÜRESİ DOLANLAR VE TAMAMLANANLAR İÇİN KESİN RENKLER) ---
@Composable
fun TodoItemRow(item: TodoItem, categories: List<Category>, onCheckedChange: (Boolean) -> Unit, onDeleteClick: () -> Unit, onTaskUpdate: (TodoItem) -> Unit) {
    var expanded by remember(item.id) { mutableStateOf(false) }
    var currentNotes by remember(item.id, item.notes) { mutableStateOf(item.notes) }
    val taskCategory = categories.find { it.id == item.categoryId }

    val remainingTimeText = calculateRemainingTime(item.dueDate, item.dueTime)
    val isOverdue = !item.isDone && remainingTimeText == "Süresi doldu!"

    // YENİ VE KESİN ÇÖZÜM: Telefonun temasını ezen sabit renkler!
    val cardColor = when {
        item.isDone -> Color.Gray.copy(alpha = 0.2f)
        isOverdue -> Color.Red.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { expanded = !expanded }.animateContentSize(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (expanded) 6.dp else 2.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = item.isDone, onCheckedChange = onCheckedChange)

                val contentAlpha = if (item.isDone) 0.5f else 1f

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        textDecoration = if (item.isDone) TextDecoration.LineThrough else TextDecoration.None,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                    )

                    if (taskCategory != null) {
                        Surface(
                            color = Color(taskCategory.colorCode).copy(alpha = if (item.isDone) 0.1f else 0.2f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        ) {
                            Text(text = taskCategory.name, color = Color(taskCategory.colorCode).copy(alpha = contentAlpha), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }

                    if (item.dueDate != null || item.dueTime != null) {
                        val dateStr = if (item.dueDate != null) formatMillisToDateString(item.dueDate) else "Her Gün"
                        val timeStr = if (item.dueTime != null) " - ${item.dueTime}" else ""
                        val bellIcon = if (item.hasReminder) " 🔔" else ""

                        Text(
                            text = "📅 $dateStr$timeStr$bellIcon",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                        )

                        if (isOverdue) {
                            Text(text = "⏳ Süresi doldu!", style = MaterialTheme.typography.bodySmall, color = Color.Red.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
                        } else if (!item.isDone && remainingTimeText != null) {
                            Text(text = "⏳ $remainingTimeText", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (item.priority != null) {
                    Row { for (i in 1..item.priority) { Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFC107).copy(alpha = contentAlpha), modifier = Modifier.size(16.dp)) } }
                }
                IconButton(onClick = onDeleteClick) { Icon(Icons.Default.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error.copy(alpha = contentAlpha)) }
            }
            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                OutlinedTextField(value = currentNotes, onValueChange = { currentNotes = it }, label = { Text("Görev Detayları / Notlar") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { onTaskUpdate(item.copy(notes = currentNotes)); expanded = false }, modifier = Modifier.align(Alignment.End)) { Text("Notu Kaydet") }
            }
        }
    }
}