package com.example.to_do_list

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

// --- YARDIMCI FONKSİYONLAR ---
fun formatMillisToDateString(millis: Long?): String {
    if (millis == null) return "Tarih Yok"
    val formatter = SimpleDateFormat("dd MMM yyyy", Locale("tr"))
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date(millis))
}

fun calculateRemainingTime(dueDateMillis: Long?, dueTimeString: String?): String? {
    if (dueDateMillis == null) return null

    val currentMillis = System.currentTimeMillis()
    val targetCalendar = Calendar.getInstance().apply { timeInMillis = dueDateMillis }

    if (dueTimeString != null) {
        val parts = dueTimeString.split(":")
        if (parts.size == 2) {
            targetCalendar.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
            targetCalendar.set(Calendar.MINUTE, parts[1].toInt())
            targetCalendar.add(Calendar.HOUR_OF_DAY, -3)
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

// --- 1. VERİ MODELLERİ ---
@Entity(tableName = "category_table")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val colorCode: Long
)

@Entity(tableName = "todo_table")
data class TodoItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val isDone: Boolean = false,
    val dueDate: Long? = null,
    val notes: String = "",
    val dueTime: String? = null,
    val priority: Int? = null,
    val categoryId: Int? = null
)

// --- 2. VERİTABANI SORGULARI ---
@Dao
interface TodoDao {
    @Query("SELECT * FROM todo_table ORDER BY isDone ASC, priority DESC, id DESC")
    fun getAll(): Flow<List<TodoItem>>

    @Insert
    suspend fun insert(item: TodoItem)

    @Delete
    suspend fun delete(item: TodoItem)

    @Update
    suspend fun update(item: TodoItem)

    @Query("SELECT * FROM category_table")
    fun getAllCategories(): Flow<List<Category>>

    @Insert
    suspend fun insertCategory(category: Category)

    // Uygulama ilk açıldığında tablo boş mu diye bakmak için
    @Query("SELECT COUNT(*) FROM category_table")
    suspend fun getCategoryCount(): Int
}

@Database(entities = [TodoItem::class, Category::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
}

enum class AppScreen { TASKS, CALENDAR, ADD_TASK }

// --- 3. ANA AKTİVİTE ---
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

// --- 4. ANA İSKELET VE VERİTABANI TOHUMLAMA (PRE-POPULATE) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(dao: TodoDao) {
    var currentScreen by remember { mutableStateOf(AppScreen.TASKS) }
    var menuExpanded by remember { mutableStateOf(false) }

    // YENİ: Uygulama açıldığında kategori yoksa hazır şablonları ekle
    LaunchedEffect(Unit) {
        if (dao.getCategoryCount() == 0) {
            dao.insertCategory(Category(name = "💊 İlaç Hatırlatıcısı", colorCode = 0xFFE53935)) // Kırmızı
            dao.insertCategory(Category(name = "🎂 Doğum Günü", colorCode = 0xFFE91E63)) // Pembe
            dao.insertCategory(Category(name = "📚 Sınav Takvimi", colorCode = 0xFF2196F3)) // Mavi
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(when(currentScreen) { AppScreen.TASKS -> "QuickDo"; AppScreen.CALENDAR -> "Takvim"; AppScreen.ADD_TASK -> "Yeni Görev Ekle" }) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                actions = {
                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, "Menü") }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Görevlerim") }, onClick = { currentScreen = AppScreen.TASKS; menuExpanded = false })
                        DropdownMenuItem(text = { Text("Takvim") }, onClick = { currentScreen = AppScreen.CALENDAR; menuExpanded = false })
                    }
                }
            )
        },
        floatingActionButton = {
            if (currentScreen == AppScreen.TASKS) {
                FloatingActionButton(onClick = { currentScreen = AppScreen.ADD_TASK }) { Icon(Icons.Default.Add, "Görev Ekle") }
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

// --- 5. GÖREV EKLEME EKRANI (AÇILIR KATEGORİ MENÜSÜ EKLENDİ) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskScreen(dao: TodoDao, onNavigateBack: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf<Long?>(null) }
    var selectedTime by remember { mutableStateOf<String?>(null) }
    var selectedPriority by remember { mutableStateOf<Int?>(null) }

    // YENİ: Kategori Durumları
    val categories by dao.getAllCategories().collectAsState(initial = emptyList())
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var expandedCategoryMenu by remember { mutableStateOf(false) }
    var showNewCategoryDialog by remember { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val datePickerState = rememberDatePickerState()
    val timePickerState = rememberTimePickerState(is24Hour = true)

    // --- YENİ KATEGORİ OLUŞTURMA DİYALOĞU ---
    if (showNewCategoryDialog) {
        var newCatName by remember { mutableStateOf("") }
        // Sabit Renk Paletimiz
        val colorPalette = listOf(0xFFE53935, 0xFFE91E63, 0xFF9C27B0, 0xFF3F51B5, 0xFF2196F3, 0xFF009688, 0xFF4CAF50, 0xFFFF9800, 0xFF795548)
        var selectedColorCode by remember { mutableStateOf(colorPalette[0]) }

        AlertDialog(
            onDismissRequest = { showNewCategoryDialog = false },
            title = { Text("Yeni Kategori") },
            text = {
                Column {
                    OutlinedTextField(value = newCatName, onValueChange = { newCatName = it }, label = { Text("Kategori Adı (Örn: Spor 🏋️)") }, singleLine = true)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Renk Seçin:", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    // Renk Paleti (Yuvarlak Butonlar)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(colorPalette) { colorCode ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(colorCode), CircleShape)
                                    .border(if (selectedColorCode == colorCode) 3.dp else 0.dp, Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .clickable { selectedColorCode = colorCode }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newCatName.isNotBlank()) {
                        scope.launch {
                            dao.insertCategory(Category(name = newCatName, colorCode = selectedColorCode))
                            showNewCategoryDialog = false
                        }
                    }
                }) { Text("Oluştur") }
            },
            dismissButton = { TextButton(onClick = { showNewCategoryDialog = false }) { Text("İptal") } }
        )
    }

    // Diğer Diyaloglar (Tarih ve Saat)
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = { selectedDate = datePickerState.selectedDateMillis; showDatePicker = false }) { Text("Tamam") } },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("İptal") } }
        ) { DatePicker(state = datePickerState) }
    }

    if (showTimePicker) {
        TimePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val hour = timePickerState.hour.toString().padStart(2, '0'); val minute = timePickerState.minute.toString().padStart(2, '0')
                    selectedTime = "$hour:$minute"; showTimePicker = false
                }) { Text("Tamam") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("İptal") } }
        ) { TimePicker(state = timePickerState, modifier = Modifier.fillMaxWidth()) }
    }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Ne yapılması gerekiyor?") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))

        // --- YENİ: KATEGORİ SEÇİCİ ---
        ExposedDropdownMenuBox(
            expanded = expandedCategoryMenu,
            onExpandedChange = { expandedCategoryMenu = !expandedCategoryMenu }
        ) {
            OutlinedTextField(
                value = selectedCategory?.name ?: "Kategori Seç (İsteğe Bağlı)",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategoryMenu) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expandedCategoryMenu,
                onDismissRequest = { expandedCategoryMenu = false }
            ) {
                // Kayıtlı Kategorileri Listele
                categories.forEach { cat ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(12.dp).background(Color(cat.colorCode), CircleShape))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(cat.name)
                            }
                        },
                        onClick = { selectedCategory = cat; expandedCategoryMenu = false }
                    )
                }
                Divider()
                // Yeni Kategori Ekleme Butonu
                DropdownMenuItem(
                    text = { Text("➕ Yeni Kategori Oluştur", color = MaterialTheme.colorScheme.primary) },
                    onClick = { expandedCategoryMenu = false; showNewCategoryDialog = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = notesText, onValueChange = { notesText = it }, label = { Text("Notlar (İsteğe bağlı)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Spacer(modifier = Modifier.height(16.dp))

        Text("Önem Derecesi (İsteğe Bağlı):", style = MaterialTheme.typography.bodyMedium)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
            for (i in 1..5) {
                IconButton(onClick = { selectedPriority = if (selectedPriority == i) null else i }) {
                    Icon(
                        imageVector = if (selectedPriority != null && i <= selectedPriority!!) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = "$i Yıldız", tint = if (selectedPriority != null && i <= selectedPriority!!) Color(0xFFFFC107) else Color.Gray, modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

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
                        // Kategori seçildiyse ID'sini, seçilmediyse null gönderiyoruz
                        dao.insert(TodoItem(title = text, dueDate = selectedDate, dueTime = selectedTime, priority = selectedPriority, notes = notesText, categoryId = selectedCategory?.id))
                        onNavigateBack()
                    }
                }
            }) { Text("Görevi Kaydet") }
        }
    }
}

// --- 6. GÖREV LİSTESİ EKRANI ---
@Composable
fun TaskListScreen(dao: TodoDao) {
    val items by dao.getAll().collectAsState(initial = emptyList())
    // YENİ: Kartlara renk basmak için kategorileri de çekiyoruz
    val categories by dao.getAllCategories().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Görev yok. Sağ alttaki + butonuna tıkla!") }
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            items(items, key = { it.id }) { item ->
                TodoItemRow(
                    item = item,
                    categories = categories, // Kategorileri karta yolluyoruz
                    onCheckedChange = { isChecked -> scope.launch { dao.update(item.copy(isDone = isChecked)) } },
                    onDeleteClick = { scope.launch { dao.delete(item) } },
                    onTaskUpdate = { updatedItem -> scope.launch { dao.update(updatedItem) } }
                )
            }
        }
    }
}

// --- 7. TAKVİM EKRANI ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarViewScreen(dao: TodoDao) {
    val items by dao.getAll().collectAsState(initial = emptyList())
    val categories by dao.getAllCategories().collectAsState(initial = emptyList()) // YENİ
    val datePickerState = rememberDatePickerState()
    val scope = rememberCoroutineScope()

    val upcomingTasks = items.filter { it.dueDate != null }.sortedBy { it.dueDate }
    val selectedDateString = formatMillisToDateString(datePickerState.selectedDateMillis)
    val tasksForSelectedDate = items.filter { item -> item.dueDate != null && formatMillisToDateString(item.dueDate) == selectedDateString }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
        item { DatePicker(state = datePickerState, modifier = Modifier.fillMaxWidth(), title = null, headline = null, showModeToggle = false) }

        if (upcomingTasks.isNotEmpty()) {
            item {
                Text("Hızlı Erişim: Tarihli Görevler", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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

        item { Text(text = if (datePickerState.selectedDateMillis == null) "Tarih Seçin" else "$selectedDateString Görevleri", modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp), style = MaterialTheme.typography.titleMedium) }

        items(tasksForSelectedDate, key = { it.id }) { item ->
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                TodoItemRow(
                    item = item,
                    categories = categories, // Kategorileri karta yolluyoruz
                    onCheckedChange = { isChecked -> scope.launch { dao.update(item.copy(isDone = isChecked)) } },
                    onDeleteClick = { scope.launch { dao.delete(item) } },
                    onTaskUpdate = { updatedItem -> scope.launch { dao.update(updatedItem) } }
                )
            }
        }
    }
}

// --- 8. GÖREV KARTI (KATEGORİ ROZETİ EKLENDİ) ---
@Composable
fun TodoItemRow(
    item: TodoItem,
    categories: List<Category>, // YENİ: Kart artık kategorileri biliyor
    onCheckedChange: (Boolean) -> Unit,
    onDeleteClick: () -> Unit,
    onTaskUpdate: (TodoItem) -> Unit
) {
    var expanded by remember(item.id) { mutableStateOf(false) }
    var currentNotes by remember(item.id, item.notes) { mutableStateOf(item.notes) }

    // Görevin ait olduğu kategoriyi bul (Eğer atandıysa)
    val taskCategory = categories.find { it.id == item.categoryId }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { expanded = !expanded }.animateContentSize(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (expanded) 6.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = item.isDone, onCheckedChange = onCheckedChange)
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = item.title, style = MaterialTheme.typography.titleMedium, textDecoration = if (item.isDone) TextDecoration.LineThrough else TextDecoration.None)

                    // YENİ: Kategori Rozeti (Badge)
                    if (taskCategory != null) {
                        Surface(
                            color = Color(taskCategory.colorCode).copy(alpha = 0.2f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        ) {
                            Text(
                                text = taskCategory.name,
                                color = Color(taskCategory.colorCode),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (item.dueDate != null) {
                        val remainingTimeText = calculateRemainingTime(item.dueDate, item.dueTime)
                        val timeStr = if (item.dueTime != null) " - ${item.dueTime}" else ""

                        Text(text = "📅 ${formatMillisToDateString(item.dueDate)}$timeStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!item.isDone && remainingTimeText != null) {
                            Text(text = "⏳ $remainingTimeText", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                if (item.priority != null) {
                    Row {
                        for (i in 1..item.priority) { Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp)) }
                    }
                }

                IconButton(onClick = onDeleteClick) { Icon(Icons.Default.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error) }
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