package com.example.to_do_list // Kendi paket adını kontrol etmeyi unutma!

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class NotificationReceiver : BroadcastReceiver() {

    // Sistem "Vakit Geldi!" dediğinde bu fonksiyon çalışır
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "QuickDo Hatırlatması"
        val message = intent.getStringExtra("message") ?: "Görev zamanı geldi!"
        val priority = intent.getIntExtra("priority", 3)
        val taskId = intent.getIntExtra("taskId", 0)

        // Bu görev tekrarlayan bir görev mi (Her Gün)?
        val isRecurring = intent.getBooleanExtra("isRecurring", false)
        val dueTimeString = intent.getStringExtra("dueTimeString")

        // 1. Bildirimi ekranda göster
        showNotification(context, title, message, priority, taskId)

        // 2. MÜHENDİSLİK HİLESİ: Eğer tekrarlayan görevse, çaldıktan sonra kendini yarın için tekrar kur!
        // (Not: scheduleNotification fonksiyonunu MainActivity'den otomatik tanır)
        if (isRecurring && dueTimeString != null) {
            scheduleNotification(context, taskId, title, message, null, dueTimeString, priority)
        }
    }

    private fun showNotification(context: Context, title: String, message: String, priority: Int, taskId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "quickdo_channel_id"

        // Android 8.0 ve üzeri için kanal oluşturma
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = if (priority >= 4) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, "Görev Hatırlatıcıları", importance).apply {
                description = "QuickDo planlanmış görev bildirimleri"
                if (priority >= 4) {
                    // Yüksek öncelikli görevler titreşimle gelir
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500)
                }
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Bildirime tıklanınca uygulamayı (MainActivity) aç
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            taskId,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Bildirimin Görsel İnşası
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Buraya ileride kendi ikonumuzu koyacağız
            .setContentTitle(if (priority >= 4) "🚨 ÖNEMLİ: $title" else "📅 $title")
            .setContentText(message)
            .setPriority(if (priority >= 4) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // Tıklanınca bildirim kaybolsun

        // Bildirimi fırlat!
        notificationManager.notify(taskId, builder.build())
    }
}