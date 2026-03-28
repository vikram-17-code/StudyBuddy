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
import com.example.studybuddy.databinding.FragmentCourseDetailBinding
import com.example.studybuddy.model.StudyHistory
import com.example.studybuddy.model.TopicDetail
import com.example.studybuddy.model.UserCourse
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
        _binding = FragmentCourseDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val userCourseId = arguments?.getString("userCourseId") ?: return
        
        setupRecyclerView(userCourseId)
        loadCourseDetails(userCourseId)

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

    private fun loadCourseDetails(id: String) {
        db.collection("user_courses").document(id).addSnapshotListener { doc, _ ->
            if (_binding == null || doc == null || !doc.exists()) return@addSnapshotListener
            val userCourse = doc.toObject(UserCourse::class.java)?.copy(userCourseId = doc.id) ?: return@addSnapshotListener
            val courseTopics = availableTopics.filter { it.planId == userCourse.planId }.sortedBy { it.topicOrder }
            
            binding.detailCourseName.text = userCourse.planId.replace("_plan", "").uppercase()
            
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
            
            // 1. Log History
            val history = StudyHistory(UUID.randomUUID().toString(), userId, topic.topicId, Timestamp.now())
            db.collection("study_history").document(history.historyId).set(history)

            // 2. Update Progress
            val updateData = mutableMapOf<String, Any>("lastStudyDate" to Timestamp.now())

            if (userCourse.currentDayNumber < topic.requiredDays) {
                updateData["currentDayNumber"] = userCourse.currentDayNumber + 1
            } else {
                val nextTopic = availableTopics.find { it.planId == userCourse.planId && it.topicOrder == topic.topicOrder + 1 }
                if (nextTopic != null) {
                    updateData["currentTopicId"] = nextTopic.topicId
                    updateData["currentDayNumber"] = 1
                } else {
                    updateData["currentTopicId"] = "COMPLETED"
                    updateData["currentDayNumber"] = topic.requiredDays + 1
                }
            }

            db.collection("user_courses").document(userCourseId).update(updateData).addOnSuccessListener {
                if (isAdded) {
                    triggerConfetti()
                    Toast.makeText(requireContext(), "Great job! Task completed!", Toast.LENGTH_SHORT).show()
                }
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
        AlertDialog.Builder(requireContext())
            .setTitle("Remove Course")
            .setMessage("Are you sure you want to remove this course from your study list? This will erase all progress.")
            .setPositiveButton("Remove") { _, _ ->
                deleteCourse(id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteCourse(id: String) {
        db.collection("user_courses").document(id).delete()
            .addOnSuccessListener {
                if (isAdded) {
                    Toast.makeText(requireContext(), "Course removed successfully", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                }
            }
            .addOnFailureListener {
                if (isAdded) {
                    Toast.makeText(requireContext(), "Failed to remove course", Toast.LENGTH_SHORT).show()
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}