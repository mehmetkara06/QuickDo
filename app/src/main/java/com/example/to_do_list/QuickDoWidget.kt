package com.example.to_do_list // Kendi paket adını kontrol et!

import android.content.Context
import android.content.Intent // Rota kütüphanesi eklendi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

// 1. Widget'ın Ekrandaki Çizimi
class QuickDoWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {

        // YENİ: Tıklayınca açılacak olan sayfanın açık rotası (Intent)
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        provideContent {
            // Widget arayüzümüz
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF1E1E1E)) // Koyu şık bir arka plan
                    .padding(16.dp)
                    // HATA DÜZELTİLDİ: Rota (Intent) doğrudan verildi
                    .clickable(actionStartActivity(mainIntent)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "✅ QuickDo Görevleri",
                    style = TextStyle(color = ColorProvider(Color.White), fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Görevlerinizi görmek için tıklayın.",
                    style = TextStyle(color = ColorProvider(Color.LightGray))
                )
            }
        }
    }
}

// 2. Android'in Widget'ı Yakalayacağı "Alıcı" (Receiver)
class QuickDoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickDoWidget()
}