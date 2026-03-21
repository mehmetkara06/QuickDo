package com.example.to_do_list // Kendi paket adını kontrol et!

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import androidx.room.Room
import kotlinx.coroutines.flow.first

// 1. Widget'ın Ekrandaki Çizimi
class QuickDoWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {

        // 1. VERİTABANINA BAĞLAN: Arka planda sessizce görevleri çekiyoruz
        val db = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "todo-db").build()
        val dao = db.todoDao()

        val allTasks = dao.getAll().first()
        val pendingTasks = allTasks.filter { !it.isDone }.take(4)

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        provideContent {
            // Widget Arayüzü Başlıyor
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color(0xFF2C2C2C))
                    .padding(12.dp)
                    .clickable(actionStartActivity(mainIntent))
            ) {
                // BAŞLIK
                Text(
                    text = "📋 QuickDo",
                    style = TextStyle(
                        // ÇÖZÜM: Hem gündüz hem gece için açıkça renkleri verdik!
                        color = ColorProvider(
                            day = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                            night = androidx.compose.ui.graphics.Color(0xFF4CAF50)
                        ),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.padding(bottom = 12.dp)
                )

                // GÖREV LİSTESİ
                if (pendingTasks.isEmpty()) {
                    Text(
                        text = "Harika! Bekleyen görev yok. 🎉",
                        style = TextStyle(
                            color = ColorProvider(
                                day = androidx.compose.ui.graphics.Color.LightGray,
                                night = androidx.compose.ui.graphics.Color.LightGray
                            ),
                            fontSize = 14.sp
                        )
                    )
                } else {
                    // Görevleri alt alta diz
                    pendingTasks.forEach { task ->
                        Row(
                            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📌",
                                modifier = GlanceModifier.padding(end = 6.dp)
                            )
                            Text(
                                text = task.title,
                                style = TextStyle(
                                    color = ColorProvider(
                                        day = androidx.compose.ui.graphics.Color.White,
                                        night = androidx.compose.ui.graphics.Color.White
                                    ),
                                    fontSize = 14.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }

                    // Eğer 4'ten fazla görev varsa ufak bir not düş
                    if (allTasks.count { !it.isDone } > 4) {
                        Text(
                            text = "+ Daha fazla görev var...",
                            style = TextStyle(
                                color = ColorProvider(
                                    day = androidx.compose.ui.graphics.Color.Gray,
                                    night = androidx.compose.ui.graphics.Color.Gray
                                ),
                                fontSize = 12.sp
                            ),
                            modifier = GlanceModifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

// 2. Android'in Widget'ı Yakalayacağı "Alıcı" (Receiver)
class QuickDoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickDoWidget()
}
