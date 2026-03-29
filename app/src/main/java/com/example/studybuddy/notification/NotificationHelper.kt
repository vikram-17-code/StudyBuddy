package com.example.studybuddy.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.studybuddy.model.TimeSlot
import java.util.*

object NotificationHelper {

    fun scheduleTimeSlotAlarm(context: Context, timeSlot: TimeSlot) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("courseName", timeSlot.name)
            putExtra("topicName", "Time to study!")
            putExtra("selectedDays", timeSlot.selectedDays.toIntArray())
        }

        // Use a unique request code based on the slotId hash
        val requestCode = timeSlot.slotId.hashCode()

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, timeSlot.hour)
            set(Calendar.MINUTE, timeSlot.minute)
            set(Calendar.SECOND, 0)
            
            // If the time has already passed today, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }
}
