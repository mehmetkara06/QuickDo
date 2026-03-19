package com.example.to_do_list // Kendi paket adını kontrol et!

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "QuickDo Hatırlatması"
        val message = intent.getStringExtra("message") ?: "Görev zamanı geldi!"
        val priority = intent.getIntExtra("priority", 3)
        val taskId = intent.getIntExtra("taskId", 0)

        showNotification(context, title, message, priority, taskId)
    }

    private fun showNotification(context: Context, title: String, message: String, priority: Int, taskId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "quickdo_channel_id"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = if (priority >= 4) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, "Görev Hatırlatıcıları", importance).apply {
                description = "QuickDo planlanmış görev bildirimleri"
                if (priority >= 4) {
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500)
                }
            }
            notificationManager.createNotificationChannel(channel)
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, taskId, tapIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(if (priority >= 4) "🚨 ÖNEMLİ: $title" else "📅 $title")
            .setContentText(message)
            .setPriority(if (priority >= 4) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(taskId, builder.build())
    }
}