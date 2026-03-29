package com.example.studybuddy.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val courseName = intent.getStringExtra("courseName") ?: "Course"
        val topicName = intent.getStringExtra("topicName") ?: "Topic"
        val selectedDays = intent.getIntArrayExtra("selectedDays")
        
        if (selectedDays != null && selectedDays.isNotEmpty()) {
            val currentDay = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
            if (!selectedDays.contains(currentDay)) {
                return // Drop the notification if today is not a selected day
            }
        }
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "study_reminders"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Study Reminders", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }
        
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Study Time: $courseName")
            .setContentText("Next topic: $topicName")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
            
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}