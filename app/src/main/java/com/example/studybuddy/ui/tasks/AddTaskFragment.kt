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
import com.example.studybuddy.R
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
        CoursePlan("daa_plan", "DAA", "https://www.geeksforgeeks.org/design-and-analysis-of-algorithms/"),
        CoursePlan("java_plan", "Java", "https://www.oracle.com/java/technologies/"),
        CoursePlan("dsa_plan", "DSA", "https://www.geeksforgeeks.org/data-structures/"),
        CoursePlan("web_plan", "Web Technology", "https://www.w3schools.com/"),
        CoursePlan("se_plan", "Software Engineering", "https://www.tutorialspoint.com/software_engineering/index.htm"),
        CoursePlan("eng_plan", "English", "https://www.britishcouncil.org/")
    )

    private val availableTopics = listOf(
        TopicDetail("daa_t1", "daa_plan", "Asymptotic Analysis", 2, "https://www.geeksforgeeks.org/analysis-of-algorithms-set-1-asymptotic-analysis/", 1),
        TopicDetail("daa_t2", "daa_plan", "Divide and Conquer", 3, "https://www.tutorialspoint.com/data_structures_algorithms/divide_and_conquer.htm", 2),
        TopicDetail("java_t1", "java_plan", "OOP Concepts", 1, "https://docs.oracle.com/javase/tutorial/java/concepts/", 1),
        TopicDetail("java_t2", "java_plan", "Collections", 2, "https://www.javatpoint.com/collections-in-java", 2),
        TopicDetail("dsa_t1", "dsa_plan", "Arrays & Linked Lists", 3, "https://www.programiz.com/dsa/linked-list", 1),
        TopicDetail("dsa_t2", "dsa_plan", "Stacks & Queues", 2, "https://www.geeksforgeeks.org/stack-data-structure/", 2),
        TopicDetail("web_t1", "web_plan", "HTML5 & CSS3", 2, "https://developer.mozilla.org/en-US/docs/Learn/Getting_started_with_the_web/HTML_basics", 1),
        TopicDetail("web_t2", "web_plan", "JavaScript Basics", 3, "https://javascript.info/", 2),
        TopicDetail("se_t1", "se_plan", "SDLC Models", 2, "https://www.tutorialspoint.com/software_engineering/software_engineering_sdlc_models.htm", 1),
        TopicDetail("se_t2", "se_plan", "Agile Methodology", 2, "https://www.atlassian.com/agile", 2),
        TopicDetail("eng_t1", "eng_plan", "Grammar & Tenses", 2, "https://www.grammarly.com/blog/verb-tenses/", 1),
        TopicDetail("eng_t2", "eng_plan", "Business Communication", 3, "https://www.coursera.org/articles/business-communication", 2)
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
        db.collection("user_courses")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { documents ->
                if (_binding == null) return@addOnSuccessListener
                val enrolledPlanIds = documents.map { it.toObject(UserCourse::class.java).planId }.toSet()
                setupCourseSpinner(enrolledPlanIds)
            }
    }

    private fun setupCourseSpinner(enrolledPlanIds: Set<String>) {
        val filteredPlans = availableCoursePlans.filter { it.planId !in enrolledPlanIds }
        if (filteredPlans.isEmpty()) {
            Toast.makeText(context, "Already joined all courses", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
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
        val selectedItem = binding.courseSpinner.selectedItem?.toString() ?: return
        val selectedPlan = availableCoursePlans.find { it.courseName == selectedItem } ?: return
        
        if (selectedHour == -1) {
            Toast.makeText(context, "Please select a time slot", Toast.LENGTH_SHORT).show()
            return
        }

        binding.saveTaskButton.isEnabled = false

        db.collection("user_courses")
            .whereEqualTo("userId", userId)
            .whereEqualTo("planId", selectedPlan.planId)
            .get()
            .addOnSuccessListener { documents ->
                if (_binding == null || !isAdded) return@addOnSuccessListener
                
                if (!documents.isEmpty) {
                    Toast.makeText(context, "Already enrolled in this course!", Toast.LENGTH_SHORT).show()
                    binding.saveTaskButton.isEnabled = true
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
                        if (isAdded) {
                            Toast.makeText(context, "Course Joined!", Toast.LENGTH_SHORT).show()
                            findNavController().popBackStack(R.id.homeFragment, false)
                        }
                    }
                    .addOnFailureListener {
                        if (isAdded) {
                            binding.saveTaskButton.isEnabled = true
                            Toast.makeText(context, "Failed to join course", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}