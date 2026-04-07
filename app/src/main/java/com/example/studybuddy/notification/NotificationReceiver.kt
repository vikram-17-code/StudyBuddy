package com.example.studybuddy.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.studybuddy.MainActivity
import com.example.studybuddy.R
import com.example.studybuddy.model.TimeSlot
import com.example.studybuddy.model.UserCourse
import com.example.studybuddy.data.CourseData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val slotId = intent.getStringExtra("slotId") ?: ""
        val slotName = intent.getStringExtra("courseName") ?: "Study Session"
        val hour = intent.getIntExtra("hour", -1)
        val minute = intent.getIntExtra("minute", -1)
        val selectedDays = intent.getIntArrayExtra("selectedDays")?.toList() ?: emptyList()

        Log.d("NotificationReceiver", "ALARM TRIGGERED for slot: $slotName")

        // 1. Fetch current study details from Firestore
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null && slotId.isNotEmpty()) {
            fetchAndShowNotification(context, userId, slotId, slotName)
        } else {
            // Fallback if not logged in
            showNotification(context, "Study Time!", "It's time for your session: $slotName")
        }

        // 2. Reschedule for the next occurrence
        if (slotId.isNotEmpty() && hour != -1 && minute != -1) {
            val timeSlot = TimeSlot(
                slotId = slotId,
                name = slotName,
                hour = hour,
                minute = minute,
                selectedDays = selectedDays
            )
            NotificationHelper.scheduleTimeSlotAlarm(context, timeSlot)
        }
    }

    private fun fetchAndShowNotification(context: Context, userId: String, slotId: String, slotName: String) {
        val db = FirebaseFirestore.getInstance()
        
        // Find courses assigned to this time slot
        db.collection("user_courses")
            .whereEqualTo("userId", userId)
            .whereEqualTo("preferredSlot", slotId)
            .get()
            .addOnSuccessListener { snapshot ->
                val userCourses = snapshot.toObjects(UserCourse::class.java)
                
                if (userCourses.isEmpty()) {
                    showNotification(context, "Study Time!", "Scheduled session: $slotName")
                    return@addOnSuccessListener
                }

                // Show notification for each active course in this slot
                userCourses.forEach { userCourse ->
                    if (userCourse.currentTopicId != "COMPLETED") {
                        resolveNamesAndNotify(context, userCourse)
                    }
                }
            }
    }

    private fun resolveNamesAndNotify(context: Context, userCourse: UserCourse) {
        // Check static data first
        val staticPlan = CourseData.availableCoursePlans.find { it.planId == userCourse.planId }
        val staticTopic = CourseData.availableTopics.find { it.topicId == userCourse.currentTopicId }

        if (staticPlan != null && staticTopic != null) {
            showNotification(context, "Study: ${staticPlan.courseName}", "Next: ${staticTopic.topicName}")
        } else {
            // Fallback to Firestore for custom courses/topics
            val db = FirebaseFirestore.getInstance()
            db.collection("course_plans").document(userCourse.planId).get().addOnSuccessListener { pDoc ->
                val courseName = pDoc.getString("courseName") ?: "Custom Course"
                db.collection("topics").document(userCourse.currentTopicId).get().addOnSuccessListener { tDoc ->
                    val topicName = tDoc.getString("topicName") ?: "Subtopic"
                    showNotification(context, "Study: $courseName", "Next: $topicName")
                }
            }
        }
    }

    private fun showNotification(context: Context, title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Ensure channel is created
        NotificationHelper.createNotificationChannel(context)
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, title.hashCode(), intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_study_buddy_logo)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
            
        notificationManager.notify(title.hashCode(), notification)
    }
}