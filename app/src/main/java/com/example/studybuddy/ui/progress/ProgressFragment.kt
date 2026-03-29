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

    private val availableTopics = com.example.studybuddy.data.CourseData.availableTopics

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        loadProgress()
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

    private fun loadProgress() {
        val userId = auth.currentUser?.uid ?: return
        
        db.collection("user_courses")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, _ ->
                if (_binding == null || !isAdded) return@addSnapshotListener
                if (snapshot != null) {
                    val userCourses = snapshot.toObjects(UserCourse::class.java).mapIndexed { index, course ->
                        course.copy(userCourseId = snapshot.documents[index].id)
                    }
                    val displayList = mutableListOf<Triple<UserCourse, List<TopicDetail>, Int>>()
                    var totalCompletion = 0f
                    var completedCount = 0

                    userCourses.forEach { userCourse ->
                        val topics = availableTopics.filter { it.planId == userCourse.planId }
                        val progress = calculateCourseProgress(userCourse, topics)
                        displayList.add(Triple(userCourse, topics, progress))
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
                
                // Group the completed courses by their lastStudyDate (chronological)
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
            
            // Map planId to drawable
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
                else -> R.drawable.ic_badge_java
            }
            
            badgeIcon.setImageResource(iconResId)
            val shortName = course.planId.replace("_plan", "").uppercase()
            badgeTitle.text = shortName

            binding.badgesGridLayout.addView(badgeView)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}