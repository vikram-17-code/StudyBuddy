package com.example.studybuddy.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.studybuddy.model.TimeSlot

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val slotId = intent.getStringExtra("slotId") ?: ""
        val courseName = intent.getStringExtra("courseName") ?: "Study Session"
        val hour = intent.getIntExtra("hour", -1)
        val minute = intent.getIntExtra("minute", -1)
        val selectedDays = intent.getIntArrayExtra("selectedDays")?.toList() ?: emptyList()

        Log.d("NotificationReceiver", "ALARM TRIGGERED for $courseName")

        // 1. Show the Notification
        showNotification(context, courseName)

        // 2. Reschedule for the next occurrence
        if (slotId.isNotEmpty() && hour != -1 && minute != -1) {
            val timeSlot = TimeSlot(
                slotId = slotId,
                name = courseName,
                hour = hour,
                minute = minute,
                selectedDays = selectedDays
            )
            NotificationHelper.scheduleTimeSlotAlarm(context, timeSlot)
        }
    }

    private fun showNotification(context: Context, courseName: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Ensure channel is created before showing
        NotificationHelper.createNotificationChannel(context)
        
        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Study Buddy Reminder")
            .setContentText("It's time for your session: $courseName")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
            
        notificationManager.notify(courseName.hashCode(), notification)
    }
}