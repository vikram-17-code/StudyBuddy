package com.example.studybuddy.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.studybuddy.MainActivity
import com.example.studybuddy.R
import com.example.studybuddy.model.TimeSlot
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

object NotificationHelper {

    const val CHANNEL_ID = "study_reminders_channel"

    fun scheduleTimeSlotAlarm(context: Context, timeSlot: TimeSlot) {
        createNotificationChannel(context)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("slotId", timeSlot.slotId)
            putExtra("courseName", timeSlot.name)
            putExtra("hour", timeSlot.hour)
            putExtra("minute", timeSlot.minute)
            putExtra("selectedDays", timeSlot.selectedDays.toIntArray())
        }

        val requestCode = timeSlot.slotId.hashCode()

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextTime = getNextOccurrence(timeSlot.hour, timeSlot.minute, timeSlot.selectedDays)

        Log.d("NotificationHelper", "Scheduling for ${timeSlot.name} at ${nextTime.time}")

        // Use setAlarmClock - most reliable on Samsung
        val alarmClockInfo = AlarmManager.AlarmClockInfo(nextTime.timeInMillis, pendingIntent)
        try {
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime.timeInMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextTime.timeInMillis, pendingIntent)
            }
        }
    }

    fun showNotification(context: Context, title: String, message: String) {
        createNotificationChannel(context)
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_study_buddy_logo)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    fun scheduleAllAlarms(context: Context) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("time_slots")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { snapshot ->
                snapshot.toObjects(TimeSlot::class.java).forEach { slot ->
                    scheduleTimeSlotAlarm(context, slot)
                }
            }
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Study Reminders"
            val descriptionText = "Notifications for your study sessions"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun getNextOccurrence(hour: Int, minute: Int, selectedDays: List<Int>): Calendar {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        if (selectedDays.isNotEmpty()) {
            var count = 0
            while (!selectedDays.contains(target.get(Calendar.DAY_OF_WEEK)) && count < 8) {
                target.add(Calendar.DAY_OF_YEAR, 1)
                count++
            }
        }
        
        return target
    }
}