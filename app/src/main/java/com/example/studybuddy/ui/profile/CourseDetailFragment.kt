package com.example.studybuddy.ui.profile

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.studybuddy.R
import com.example.studybuddy.databinding.FragmentCourseDetailBinding
import com.example.studybuddy.model.CoursePlan
import com.example.studybuddy.model.StudyHistory
import com.example.studybuddy.model.TopicDetail
import com.example.studybuddy.model.UserCourse
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.util.UUID
import java.util.concurrent.TimeUnit

class CourseDetailFragment : Fragment() {
    private var _binding: FragmentCourseDetailBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private lateinit var adapter: TopicStatusAdapter

    private val staticTopics = com.example.studybuddy.data.CourseData.availableTopics
    private val staticPlans = com.example.studybuddy.data.CourseData.availableCoursePlans
    
    private var customTopics: List<TopicDetail> = emptyList()
    private var customPlans: List<CoursePlan> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCourseDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val userCourseId = arguments?.getString("userCourseId") ?: return
        
        setupRecyclerView(userCourseId)
        loadCustomData(userCourseId)

        binding.deleteCourseButton.setOnClickListener {
            showDeleteConfirmation(userCourseId)
        }
    }

    private fun setupRecyclerView(userCourseId: String) {
        adapter = TopicStatusAdapter { topic ->
            handleTaskCompletion(userCourseId, topic)
        }
        binding.curriculumRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.curriculumRecyclerView.adapter = adapter
    }

    private fun loadCustomData(id: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("course_plans").whereEqualTo("userId", userId).get().addOnSuccessListener { plansSnap ->
            customPlans = plansSnap.toObjects(CoursePlan::class.java)
            db.collection("topics").get().addOnSuccessListener { topicsSnap ->
                customTopics = topicsSnap.toObjects(TopicDetail::class.java)
                loadCourseDetails(id)
            }
        }
    }

    private fun loadCourseDetails(id: String) {
        db.collection("user_courses").document(id).addSnapshotListener { doc, _ ->
            if (_binding == null || doc == null || !doc.exists()) return@addSnapshotListener
            val userCourse = doc.toObject(UserCourse::class.java)?.copy(userCourseId = doc.id) ?: return@addSnapshotListener
            
            val allTopics = staticTopics + customTopics
            val allPlans = staticPlans + customPlans
            
            val courseTopics = allTopics.filter { it.planId == userCourse.planId }.sortedBy { it.topicOrder }
            val plan = allPlans.find { it.planId == userCourse.planId }
            
            binding.detailCourseName.text = plan?.courseName ?: userCourse.planId.replace("_plan", "").uppercase()
            
            val progress = calculateProgress(userCourse, courseTopics)
            binding.courseDetailPieChart.setProgress(progress.toFloat())
            binding.courseDetailStatus.text = "Overall Progress: $progress%"
            
            adapter.submitList(courseTopics, userCourse)
        }
    }

    private fun calculateProgress(userCourse: UserCourse, topics: List<TopicDetail>): Int {
        if (topics.isEmpty()) return 0
        if (userCourse.currentTopicId == "COMPLETED") return 100
        
        val totalDays = topics.sumOf { it.requiredDays }
        var completedDays = 0
        
        val currentTopic = topics.find { it.topicId == userCourse.currentTopicId } ?: return 100
        
        for (topic in topics) {
            if (topic.topicId == userCourse.currentTopicId) {
                completedDays += (userCourse.currentDayNumber - 1)
                break
            } else if (topic.topicOrder < currentTopic.topicOrder) {
                completedDays += topic.requiredDays
            }
        }
        
        return (completedDays * 100) / totalDays
    }

    private fun handleTaskCompletion(userCourseId: String, topic: TopicDetail) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        db.collection("user_courses").document(userCourseId).get().addOnSuccessListener { doc ->
            val userCourse = doc.toObject(UserCourse::class.java)?.copy(userCourseId = doc.id) ?: return@addOnSuccessListener
            
            val history = StudyHistory(UUID.randomUUID().toString(), userId, topic.topicId, Timestamp.now())
            db.collection("study_history").document(history.historyId).set(history)

            val updateData = mutableMapOf<String, Any>("lastStudyDate" to Timestamp.now())
            var isFullyCompleted = false

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
                    isFullyCompleted = true
                }
            }

            // OPTIMISTIC UPDATE
            db.collection("user_courses").document(userCourseId).update(updateData)
            
            if (isAdded) {
                triggerConfetti()
                val message = if (isFullyCompleted) "Congratulations! You have completed the entire course!" else "Task for today completed!"
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Task Complete")
                    .setMessage(message)
                    .setPositiveButton("Awesome") { _, _ ->
                        if (isFullyCompleted) findNavController().navigate(R.id.progressFragment)
                    }
                    .show()
            }
        }
    }

    private fun triggerConfetti() {
        val party = Party(
            speed = 0f,
            maxSpeed = 30f,
            damping = 0.9f,
            spread = 360,
            colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def),
            position = Position.Relative(0.5, 0.3),
            emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(100)
        )
        binding.konfettiView.start(party)
    }

    private fun showDeleteConfirmation(id: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Remove Course")
            .setMessage("Are you sure you want to remove this course from your study list? This will erase all progress.")
            .setPositiveButton("Remove") { _, _ ->
                deleteCourse(id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteCourse(id: String) {
        // OPTIMISTIC DELETION
        db.collection("user_courses").document(id).delete()
        
        if (isAdded) {
            Toast.makeText(requireContext(), "Course Removed Successfully!", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.profileFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}