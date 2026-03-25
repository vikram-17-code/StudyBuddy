package com.example.studybuddy.ui.schedule

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.studybuddy.databinding.FragmentScheduleBinding
import com.example.studybuddy.model.TopicDetail
import com.example.studybuddy.model.UserCourse
import com.example.studybuddy.ui.home.TaskAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*
import java.util.concurrent.TimeUnit

class ScheduleFragment : Fragment() {
    private var _binding: FragmentScheduleBinding? = null
    private val binding get() = _binding!!
    private lateinit var taskAdapter: TaskAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val availableTopics = listOf(
        TopicDetail("daa_t1", "daa_plan", "Asymptotic Analysis", 2, "link_to_daa_1", 1),
        TopicDetail("daa_t2", "daa_plan", "Divide and Conquer", 3, "link_to_daa_2", 2),
        TopicDetail("java_t1", "java_plan", "OOP Concepts", 1, "link_to_java_1", 1),
        TopicDetail("java_t2", "java_plan", "Collections", 2, "link_to_java_2", 2)
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        
        loadTasksForDate(Calendar.getInstance())

        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val calendar = Calendar.getInstance().apply {
                set(year, month, dayOfMonth, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            loadTasksForDate(calendar)
        }
    }

    private fun setupRecyclerView() {
        taskAdapter = TaskAdapter { _, _ -> }
        binding.scheduleRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = taskAdapter
        }
    }

    private fun loadTasksForDate(selectedDate: Calendar) {
        val userId = auth.currentUser?.uid ?: return
        
        db.collection("user_courses")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { snapshot ->
                val taskList = mutableListOf<Pair<UserCourse, TopicDetail>>()
                for (doc in snapshot) {
                    val userCourse = doc.toObject(UserCourse::class.java)
                    val expectedTopic = calculateExpectedTopic(userCourse, selectedDate)
                    if (expectedTopic != null) {
                        taskList.add(userCourse to expectedTopic)
                    }
                }
                taskAdapter.submitList(taskList)
                
                if (taskList.isEmpty()) {
                    Toast.makeText(requireContext(), "No tasks projected for this date", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun calculateExpectedTopic(userCourse: UserCourse, selectedDate: Calendar): TopicDetail? {
        val courseTopics = availableTopics.filter { it.planId == userCourse.planId }.sortedBy { it.topicOrder }
        if (courseTopics.isEmpty()) return null

        val startCal = Calendar.getInstance().apply {
            time = userCourse.startDate.toDate()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Days between start and selected date
        val diffInMillis = selectedDate.timeInMillis - startCal.timeInMillis
        if (diffInMillis < 0) return null // Selected date is before course start

        val daysFromStart = TimeUnit.MILLISECONDS.toDays(diffInMillis).toInt()
        
        var currentDayCounter = 0
        for (topic in courseTopics) {
            val topicEndDay = currentDayCounter + topic.requiredDays
            if (daysFromStart < topicEndDay) {
                return topic
            }
            currentDayCounter = topicEndDay
        }
        
        return null // Course expected to be finished
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}