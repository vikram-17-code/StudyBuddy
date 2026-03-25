package com.example.studybuddy.ui.tasks

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.studybuddy.databinding.FragmentAddTaskBinding
import com.example.studybuddy.model.CoursePlan
import com.example.studybuddy.model.TopicDetail
import com.example.studybuddy.model.UserCourse
import com.example.studybuddy.notification.NotificationReceiver
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class AddTaskFragment : Fragment() {

    private var _binding: FragmentAddTaskBinding? = null
    private val binding get() = _binding!!
    private lateinit var topicAdapter: TopicListAdapter
    private var selectedHour: Int = -1
    private var selectedMinute: Int = -1
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val availableCoursePlans = listOf(
        CoursePlan("daa_plan", "DAA"),
        CoursePlan("java_plan", "Java")
    )

    private val availableTopics = listOf(
        TopicDetail("daa_t1", "daa_plan", "Asymptotic Analysis", 2, "link_to_daa_1", 1),
        TopicDetail("daa_t2", "daa_plan", "Divide and Conquer", 3, "link_to_daa_2", 2),
        TopicDetail("java_t1", "java_plan", "OOP Concepts", 1, "link_to_java_1", 1),
        TopicDetail("java_t2", "java_plan", "Collections", 2, "link_to_java_2", 2)
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        checkUserEnrollment()

        binding.pickTimeButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            TimePickerDialog(requireContext(), { _, hourOfDay, minute ->
                selectedHour = hourOfDay
                selectedMinute = minute
                binding.selectedTimeTextView.text = String.format(Locale.getDefault(), "Selected Slot: %02d:%02d", hourOfDay, minute)
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
        }

        binding.saveTaskButton.setOnClickListener {
            confirmCoursePlan()
        }
    }

    private fun setupRecyclerView() {
        topicAdapter = TopicListAdapter()
        binding.topicsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = topicAdapter
        }
    }

    private fun checkUserEnrollment() {
        val userId = auth.currentUser?.uid ?: return
        binding.saveTaskButton.isEnabled = false
        
        db.collection("user_courses")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { documents ->
                val enrolledPlanIds = documents.map { it.toObject(UserCourse::class.java).planId }.toSet()
                setupCourseSpinner(enrolledPlanIds)
                binding.saveTaskButton.isEnabled = true
            }
    }

    private fun setupCourseSpinner(enrolledPlanIds: Set<String>) {
        val filteredPlans = availableCoursePlans.filter { it.planId !in enrolledPlanIds }
        if (filteredPlans.isEmpty()) {
            Toast.makeText(requireContext(), "Already joined all courses", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        val names = filteredPlans.map { it.courseName }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.courseSpinner.adapter = adapter

        binding.courseSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedPlanId = filteredPlans[position].planId
                topicAdapter.submitList(availableTopics.filter { it.planId == selectedPlanId })
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun confirmCoursePlan() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("user_courses")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { documents ->
                val enrolledPlanIds = documents.map { it.toObject(UserCourse::class.java).planId }.toSet()
                val filteredPlans = availableCoursePlans.filter { it.planId !in enrolledPlanIds }
                if (filteredPlans.isEmpty()) return@addOnSuccessListener
                
                val selectedPlan = filteredPlans[binding.courseSpinner.selectedItemPosition]
                if (selectedHour == -1) {
                    Toast.makeText(requireContext(), "Please select a time slot", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val preferredSlot = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute)
                val firstTopic = availableTopics.find { it.planId == selectedPlan.planId && it.topicOrder == 1 }
                
                val userCourse = UserCourse(
                    userCourseId = UUID.randomUUID().toString(),
                    userId = userId,
                    planId = selectedPlan.planId,
                    currentTopicId = firstTopic?.topicId ?: "",
                    currentDayNumber = 1,
                    preferredSlot = preferredSlot
                )

                db.collection("user_courses")
                    .document(userCourse.userCourseId)
                    .set(userCourse)
                    .addOnSuccessListener {
                        scheduleNotification(selectedPlan.courseName, firstTopic?.topicName ?: "")
                        Toast.makeText(requireContext(), "Course Joined!", Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                    }
            }
    }

    private fun scheduleNotification(courseName: String, topicName: String) {
        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(requireContext(), NotificationReceiver::class.java).apply {
            putExtra("courseName", courseName)
            putExtra("topicName", topicName)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            requireContext(), 
            courseName.hashCode(), 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, selectedHour)
            set(Calendar.MINUTE, selectedMinute)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DATE, 1) // Schedule for tomorrow if time already passed
            }
        }

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}