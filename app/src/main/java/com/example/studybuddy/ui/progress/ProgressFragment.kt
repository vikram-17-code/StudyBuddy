package com.example.studybuddy.ui.progress

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.studybuddy.R
import com.example.studybuddy.databinding.FragmentProgressBinding
import com.example.studybuddy.model.CoursePlan
import com.example.studybuddy.model.TopicDetail
import com.example.studybuddy.model.UserCourse
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProgressFragment : Fragment() {
    private var _binding: FragmentProgressBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var adapter: CourseProgressAdapter

    private val staticTopics = com.example.studybuddy.data.CourseData.availableTopics
    private val staticPlans = com.example.studybuddy.data.CourseData.availableCoursePlans

    private var customTopics: List<TopicDetail> = emptyList()
    private var customPlans: List<CoursePlan> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        loadCustomDataAndProgress()
        loadUserBadges()
    }

    private fun setupRecyclerView() {
        adapter = CourseProgressAdapter { userCourse ->
            val bundle = Bundle().apply {
                putString("userCourseId", userCourse.userCourseId)
            }
            findNavController().navigate(R.id.action_progressFragment_to_courseDetailFragment, bundle)
        }
        binding.courseProgressRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.courseProgressRecyclerView.adapter = adapter
    }

    private fun loadCustomDataAndProgress() {
        val userId = auth.currentUser?.uid ?: return
        
        // Load custom plans and topics first, then load progress
        db.collection("course_plans").whereEqualTo("userId", userId).get().addOnSuccessListener { plansSnap ->
            customPlans = plansSnap.toObjects(CoursePlan::class.java)
            
            db.collection("topics").get().addOnSuccessListener { topicsSnap ->
                customTopics = topicsSnap.toObjects(TopicDetail::class.java)
                loadProgress()
            }
        }
    }

    private fun loadProgress() {
        val userId = auth.currentUser?.uid ?: return
        val allTopics = staticTopics + customTopics
        val allPlans = staticPlans + customPlans

        db.collection("user_courses")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, _ ->
                if (_binding == null || !isAdded) return@addSnapshotListener
                if (snapshot != null) {
                    val userCourses = snapshot.map { it.toObject(UserCourse::class.java).copy(userCourseId = it.id) }
                    
                    val displayList = mutableListOf<CourseProgressItem>()
                    var totalCompletion = 0f
                    var completedCount = 0

                    userCourses.forEach { userCourse ->
                        val topics = allTopics.filter { it.planId == userCourse.planId }
                        val plan = allPlans.find { it.planId == userCourse.planId }
                        val courseName = plan?.courseName ?: userCourse.planId.replace("_plan", "").uppercase()
                        
                        val progress = calculateCourseProgress(userCourse, topics)
                        displayList.add(CourseProgressItem(userCourse, topics, courseName, progress))
                        totalCompletion += progress
                        if (progress == 100 || userCourse.currentTopicId == "COMPLETED") {
                            completedCount++
                        }
                    }

                    adapter.submitList(displayList)
                    
                    val overallProgress = if (userCourses.isNotEmpty()) totalCompletion / userCourses.size else 0f
                    binding.overallPieChart.setProgress(overallProgress)

                    val enrolledCount = userCourses.size
                    binding.enrollmentStatsText.text = "$enrolledCount Courses Enrolled | $completedCount Completed"

                    val msg = when {
                        enrolledCount == 0 -> "Start enrolling to see your progress!"
                        completedCount == enrolledCount -> "Amazing! You've finished all your courses."
                        overallProgress > 75f -> "Almost there, finish strong!"
                        overallProgress > 40f -> "Great job! Keep up the good work."
                        else -> "Keep going to unlock badges!"
                    }
                    binding.encouragementText.text = msg
                }
            }
    }

    private fun calculateCourseProgress(userCourse: UserCourse, topics: List<TopicDetail>): Int {
        if (topics.isEmpty()) return 0
        if (userCourse.currentTopicId == "COMPLETED") return 100
        
        val totalDays = topics.sumOf { it.requiredDays }
        var completedDays = 0
        
        val currentTopic = topics.find { it.topicId == userCourse.currentTopicId } ?: return 100
        
        for (topic in topics.sortedBy { it.topicOrder }) {
            if (topic.topicId == userCourse.currentTopicId) {
                completedDays += (userCourse.currentDayNumber - 1)
                break
            } else if (topic.topicOrder < currentTopic.topicOrder) {
                completedDays += topic.requiredDays
            }
        }
        
        return (completedDays * 100) / totalDays
    }

    private fun loadUserBadges() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("user_courses")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, _ ->
                if (_binding == null || snapshot == null) return@addSnapshotListener
                val completedCourses = snapshot.documents.mapNotNull { doc ->
                    val course = doc.toObject(UserCourse::class.java)
                    if (course?.currentTopicId == "COMPLETED") course else null
                }.sortedBy { it.lastStudyDate }

                updateBadgesUI(completedCourses)
            }
    }

    private fun updateBadgesUI(completedCourses: List<UserCourse>) {
        if (_binding == null) return
        binding.badgesGridLayout.removeAllViews()
        if (completedCourses.isEmpty()) {
            binding.badgesGridLayout.visibility = View.GONE
            return
        } else {
            binding.badgesGridLayout.visibility = View.VISIBLE
        }

        for (course in completedCourses) {
            val badgeView = LayoutInflater.from(requireContext()).inflate(R.layout.item_badge, binding.badgesGridLayout, false)
            val badgeIcon = badgeView.findViewById<android.widget.ImageView>(R.id.badgeIcon)
            val badgeTitle = badgeView.findViewById<android.widget.TextView>(R.id.badgeTitle)
            
            val iconResId = when (course.planId) {
                "daa_plan" -> R.drawable.ic_badge_daa
                "java_plan" -> R.drawable.ic_badge_java
                "dsa_plan" -> R.drawable.ic_badge_dsa
                "web_plan" -> R.drawable.ic_badge_web
                "se_plan" -> R.drawable.ic_badge_se
                "eng_plan" -> R.drawable.ic_badge_eng
                "python_plan" -> R.drawable.ic_badge_python
                "os_plan" -> R.drawable.ic_badge_os
                "oss_plan" -> R.drawable.ic_badge_oss
                "ai_plan" -> R.drawable.ic_badge_ai
                "animation_plan" -> R.drawable.ic_badge_animation
                else -> R.drawable.ic_badge_java // Default badge for custom courses
            }
            
            badgeIcon.setImageResource(iconResId)
            val plan = (staticPlans + customPlans).find { it.planId == course.planId }
            badgeTitle.text = plan?.courseName ?: course.planId.replace("_plan", "").uppercase()

            binding.badgesGridLayout.addView(badgeView)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}