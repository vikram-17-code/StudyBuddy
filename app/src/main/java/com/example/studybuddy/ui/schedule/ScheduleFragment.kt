package com.example.studybuddy.ui.schedule

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.studybuddy.R
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
        // HIDE DAY INFO AND COMPLETE BUTTON, AND ADD CLICK NAVIGATION
        taskAdapter = TaskAdapter(
            showCompleteButton = false,
            showDayInfo = false,
            onTaskClick = { userCourse ->
                val bundle = Bundle().apply {
                    putString("userCourseId", userCourse.userCourseId)
                }
                findNavController().navigate(R.id.action_scheduleFragment_to_courseDetailFragment, bundle)
            }
        )
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
                if (_binding == null) return@addOnSuccessListener
                
                val taskList = mutableListOf<Pair<UserCourse, TopicDetail>>()
                for (doc in snapshot) {
                    val userCourse = doc.toObject(UserCourse::class.java).copy(userCourseId = doc.id)
                    
                    // ONLY SHOW INCOMPLETE COURSES
                    if (userCourse.currentTopicId != "COMPLETED") {
                        val expectedTopic = calculateExpectedTopic(userCourse, selectedDate)
                        if (expectedTopic != null) {
                            taskList.add(userCourse to expectedTopic)
                        }
                    }
                }
                taskAdapter.submitList(taskList)
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

        val diffInMillis = selectedDate.timeInMillis - startCal.timeInMillis
        if (diffInMillis < 0) return null 

        val daysFromStart = TimeUnit.MILLISECONDS.toDays(diffInMillis).toInt()
        
        var currentDayCounter = 0
        for (topic in courseTopics) {
            val topicEndDay = currentDayCounter + topic.requiredDays
            if (daysFromStart < topicEndDay) {
                return topic
            }
            currentDayCounter = topicEndDay
        }
        
        return null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}