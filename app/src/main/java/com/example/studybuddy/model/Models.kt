package com.example.studybuddy.model

import com.google.firebase.Timestamp

// Models mapped to the ER diagram
data class UserProfile(
    val userId: String = "",
    val name: String = "",
    val email: String = ""
)

data class CoursePlan(
    val planId: String = "",
    val courseName: String = ""
)

data class TopicDetail(
    val topicId: String = "",
    val planId: String = "",
    val topicName: String = "",
    val requiredDays: Int = 1,
    val materialLink: String = "",
    val topicOrder: Int = 0
)

data class UserCourse(
    val userCourseId: String = "",
    val userId: String = "",
    val planId: String = "",
    val currentTopicId: String = "",
    val currentDayNumber: Int = 1,
    val preferredSlot: String = "",
    val lastStudyDate: Timestamp? = null,
    val startDate: Timestamp = Timestamp.now()
)

data class StudyHistory(
    val historyId: String = "",
    val userId: String = "",
    val topicId: String = "",
    val studyDate: Timestamp = Timestamp.now()
)

data class AppNotification(
    val notificationId: String = "",
    val userId: String = "",
    val notificationType: String = "",
    val message: String = "",
    val sentTime: Timestamp = Timestamp.now(),
    val isRead: Boolean = false
)