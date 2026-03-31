package com.example.studybuddy.ui.home

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
import com.example.studybuddy.databinding.FragmentHomeBinding
import com.example.studybuddy.model.CoursePlan
import com.example.studybuddy.model.StudyHistory
import com.example.studybuddy.model.TopicDetail
import com.example.studybuddy.model.UserCourse
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar
import java.util.UUID

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var todayTaskAdapter: TaskAdapter
    private lateinit var upcomingTaskAdapter: TaskAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var userDataListener: ListenerRegistration? = null
    private var timeSlots: List<com.example.studybuddy.model.TimeSlot> = emptyList()
    private var userCoursesList: List<UserCourse> = emptyList()
    private var customTopics: List<TopicDetail> = emptyList()
    private var customCoursePlans: List<CoursePlan> = emptyList()

    private val staticTopics = com.example.studybuddy.data.CourseData.availableTopics
    private val staticPlans = com.example.studybuddy.data.CourseData.availableCoursePlans

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        updateGreeting()
        loadUserData()
        loadTimeSlots()
        loadUserTasks()
        loadCustomData()
        
        binding.addTaskFab.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_addTaskFragment)
        }
    }

    private fun updateGreeting() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 0..11 -> "Good Morning,"
            in 12..16 -> "Good Afternoon,"
            else -> "Good Evening,"
        }
        binding.greetingTextView.text = greeting
    }

    private fun loadUserData() {
        val user = auth.currentUser ?: return
        userDataListener = db.collection("users").document(user.uid)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                if (_binding != null && snapshot != null && snapshot.exists()) {
                    val name = snapshot.getString("name") ?: "Student"
                    binding.userNameTextView.text = "$name!"
                }
            }
    }

    private fun setupRecyclerView() {
        todayTaskAdapter = TaskAdapter(
            showCompleteButton = true,
            showDayInfo = true,
            onTaskClick = { userCourse ->
                val bundle = Bundle().apply { putString("userCourseId", userCourse.userCourseId) }
                findNavController().navigate(R.id.action_homeFragment_to_courseDetailFragment, bundle)
            },
            onCompleteClick = { userCourse, topic ->
                handleTaskCompletion(userCourse, topic)
            }
        )
        binding.todayTasksRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = todayTaskAdapter
        }

        upcomingTaskAdapter = TaskAdapter(
            showCompleteButton = false,
            showDayInfo = true,
            onTaskClick = { userCourse ->
                val bundle = Bundle().apply { putString("userCourseId", userCourse.userCourseId) }
                findNavController().navigate(R.id.action_homeFragment_to_courseDetailFragment, bundle)
            }
        )
        binding.upcomingTasksRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = upcomingTaskAdapter
        }
    }

    private fun loadTimeSlots() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("time_slots")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, _ ->
                if (_binding == null) return@addSnapshotListener
                val slots = snapshot?.toObjects(com.example.studybuddy.model.TimeSlot::class.java) ?: emptyList()
                timeSlots = slots
                processTasks()
            }
    }

    private fun loadUserTasks() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("user_courses")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, _ ->
                if (_binding == null || !isAdded) return@addSnapshotListener 
                if (snapshot != null) {
                    userCoursesList = snapshot.map { it.toObject(UserCourse::class.java).copy(userCourseId = it.id) }
                    processTasks()
                }
            }
    }

    private fun loadCustomData() {
        val userId = auth.currentUser?.uid ?: return
        
        // Fetch custom course plans for this user
        db.collection("course_plans")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, _ ->
                if (_binding == null || snapshot == null) return@addSnapshotListener
                customCoursePlans = snapshot.toObjects(CoursePlan::class.java)
                processTasks()
            }

        // Fetch custom topics
        db.collection("topics")
            .addSnapshotListener { snapshot, _ ->
                if (_binding == null || snapshot == null) return@addSnapshotListener
                customTopics = snapshot.toObjects(TopicDetail::class.java)
                processTasks()
            }
    }

    private fun processTasks() {
        if (_binding == null) return
        val currentDayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        val today = Calendar.getInstance()
        
        val todayList = mutableListOf<Triple<UserCourse, TopicDetail, String>>()
        val upcomingList = mutableListOf<Triple<UserCourse, TopicDetail, String>>()
        val totalTasks = userCoursesList.size

        val allTopics = staticTopics + customTopics
        val allPlans = staticPlans + customCoursePlans

        for (userCourse in userCoursesList) {
            if (userCourse.currentTopicId == "COMPLETED") continue

            val currentTopic = allTopics.find { it.topicId == userCourse.currentTopicId } ?: continue
            val plan = allPlans.find { it.planId == userCourse.planId }
            val courseName = plan?.courseName ?: userCourse.planId.replace("_plan", "").uppercase()
            
            val timeSlot = timeSlots.find { it.slotId == userCourse.preferredSlot }
            val isScheduledToday = timeSlot != null && timeSlot.selectedDays.contains(currentDayOfWeek)
            
            var studiedToday = false
            if (userCourse.lastStudyDate != null) {
                val studyCal = Calendar.getInstance().apply { time = userCourse.lastStudyDate.toDate() }
                if (studyCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    studyCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) {
                    studiedToday = true
                }
            }

            if (isScheduledToday && !studiedToday) {
                todayList.add(Triple(userCourse, currentTopic, courseName))
            } else {
                upcomingList.add(Triple(userCourse, currentTopic, courseName))
            }
        }

        todayTaskAdapter.submitList(todayList)
        upcomingTaskAdapter.submitList(upcomingList)

        binding.freeTodayTextView.visibility = if (todayList.isEmpty()) View.VISIBLE else View.GONE
        updateProgressDashboard(todayList.size, totalTasks)
    }

    private fun updateProgressDashboard(pendingTasks: Int, totalTasks: Int) {
        val activeTasks = pendingTasks
        binding.dailyTaskCountTextView.text = if (activeTasks == 0) "All caught up!" else "$activeTasks Tasks Today"
        val progress = if (totalTasks > 0) ((totalTasks - activeTasks) * 100) / totalTasks else 0
        binding.dailyCircularProgress.setProgress(progress, true)
    }

    private fun handleTaskCompletion(userCourse: UserCourse, topic: TopicDetail) {
        val userId = auth.currentUser?.uid ?: return
        val userCourseId = userCourse.userCourseId
        
        val history = StudyHistory(UUID.randomUUID().toString(), userId, topic.topicId, Timestamp.now())
        db.collection("study_history").document(history.historyId).set(history)

        val updateData = mutableMapOf<String, Any>("lastStudyDate" to Timestamp.now())

        if (userCourse.currentDayNumber < topic.requiredDays) {
            updateData["currentDayNumber"] = userCourse.currentDayNumber + 1
        } else {
            val allTopics = staticTopics + customTopics
            val nextTopic = allTopics.find { it.planId == userCourse.planId && it.topicOrder == topic.topicOrder + 1 }
            if (nextTopic != null) {
                updateData["currentTopicId"] = nextTopic.topicId
                updateData["currentDayNumber"] = 1
            } else {
                updateData["currentTopicId"] = "COMPLETED"
                updateData["currentDayNumber"] = topic.requiredDays + 1
            }
        }

        db.collection("user_courses").document(userCourseId).update(updateData)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        userDataListener?.remove()
        _binding = null
    }
}