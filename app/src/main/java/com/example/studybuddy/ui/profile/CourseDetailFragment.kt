package com.example.studybuddy.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.studybuddy.databinding.FragmentCourseDetailBinding
import com.example.studybuddy.model.TopicDetail
import com.example.studybuddy.model.UserCourse
import com.google.firebase.firestore.FirebaseFirestore

class CourseDetailFragment : Fragment() {
    private var _binding: FragmentCourseDetailBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()

    private val availableTopics = listOf(
        TopicDetail("daa_t1", "daa_plan", "Asymptotic Analysis", 2, "link_to_daa_1", 1),
        TopicDetail("daa_t2", "daa_plan", "Divide and Conquer", 3, "link_to_daa_2", 2),
        TopicDetail("java_t1", "java_plan", "OOP Concepts", 1, "link_to_java_1", 1),
        TopicDetail("java_t2", "java_plan", "Collections", 2, "link_to_java_2", 2)
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCourseDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val userCourseId = arguments?.getString("userCourseId") ?: return
        loadCourseDetails(userCourseId)
    }

    private fun loadCourseDetails(id: String) {
        db.collection("user_courses").document(id).get().addOnSuccessListener { doc ->
            val userCourse = doc.toObject(UserCourse::class.java) ?: return@addOnSuccessListener
            val courseTopics = availableTopics.filter { it.planId == userCourse.planId }
            
            binding.detailCourseName.text = userCourse.planId.replace("_plan", "").uppercase()
            
            // Calculate progress
            val totalDays = courseTopics.sumOf { it.requiredDays }
            var completedDays = 0
            for (topic in courseTopics) {
                if (topic.topicId == userCourse.currentTopicId) {
                    completedDays += userCourse.currentDayNumber - 1
                    break
                } else {
                    val currentTopic = courseTopics.find { it.topicId == userCourse.currentTopicId }
                    if (topic.topicOrder < (currentTopic?.topicOrder ?: 0)) {
                        completedDays += topic.requiredDays
                    }
                }
            }
            
            val progress = if (totalDays > 0) (completedDays * 100) / totalDays else 0
            binding.courseDetailProgressBar.progress = progress
            binding.courseDetailStatus.text = "Progress: $progress%"
            
            setupCurriculumList(courseTopics, userCourse)
        }
    }

    private fun setupCurriculumList(topics: List<TopicDetail>, userCourse: UserCourse) {
        // Simple implementation: you could create another adapter for this
        binding.curriculumRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        // For brevity, using a simple adapter approach here or placeholder
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}