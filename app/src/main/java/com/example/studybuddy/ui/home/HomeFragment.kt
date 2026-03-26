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
import com.example.studybuddy.model.StudyHistory
import com.example.studybuddy.model.TopicDetail
import com.example.studybuddy.model.UserCourse
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var taskAdapter: TaskAdapter
    private val db = FirebaseFirestore.getInstance()

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
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        loadUserTasks()
        binding.addTaskFab.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_addTaskFragment)
        }
    }

    private fun setupRecyclerView() {
        // Use explicit parameter names to avoid confusion with TaskAdapter's multiple lambdas
        taskAdapter = TaskAdapter(
            showCompleteButton = true,
            onTaskClick = { userCourse ->
                val bundle = Bundle().apply {
                    putString("userCourseId", userCourse.userCourseId)
                }
                findNavController().navigate(R.id.action_homeFragment_to_courseDetailFragment, bundle)
            },
            onCompleteClick = { userCourse, topic ->
                handleTaskCompletion(userCourse, topic)
            }
        )
        binding.tasksRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = taskAdapter
        }
    }

    private fun loadUserTasks() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("user_courses")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, e ->
                if (_binding == null || !isAdded) return@addSnapshotListener 
                if (e != null) {
                    Log.e("HomeFragment", "Listen failed", e)
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val taskList = mutableListOf<Pair<UserCourse, TopicDetail>>()
                    for (doc in snapshot) {
                        // CRITICAL: Must map document ID to userCourseId for updates to work
                        val userCourse = doc.toObject(UserCourse::class.java).copy(userCourseId = doc.id)
                        val currentTopic = availableTopics.find { it.topicId == userCourse.currentTopicId }
                        if (currentTopic != null) {
                            taskList.add(userCourse to currentTopic)
                        }
                    }
                    taskAdapter.submitList(taskList)
                }
            }
    }

    private fun handleTaskCompletion(userCourse: UserCourse, topic: TopicDetail) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val userCourseId = userCourse.userCourseId
        
        if (userCourseId.isEmpty()) {
            Toast.makeText(requireContext(), "Error: Course data sync error", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Log History
        val history = StudyHistory(UUID.randomUUID().toString(), userId, topic.topicId, Timestamp.now())
        db.collection("study_history").document(history.historyId).set(history)

        // 2. Update Progress
        val updateData = mutableMapOf<String, Any>("lastStudyDate" to Timestamp.now())

        if (userCourse.currentDayNumber < topic.requiredDays) {
            updateData["currentDayNumber"] = userCourse.currentDayNumber + 1
        } else {
            // Find next topic in order
            val nextTopic = availableTopics.find { it.planId == userCourse.planId && it.topicOrder == topic.topicOrder + 1 }
            if (nextTopic != null) {
                updateData["currentTopicId"] = nextTopic.topicId
                updateData["currentDayNumber"] = 1
            } else {
                // Course fully completed - mark as such so calculators show 100%
                updateData["currentTopicId"] = "COMPLETED"
                updateData["currentDayNumber"] = topic.requiredDays + 1
                if (isAdded) {
                    Toast.makeText(requireContext(), "Congratulations! You've finished this course!", Toast.LENGTH_LONG).show()
                }
            }
        }

        db.collection("user_courses").document(userCourseId)
            .update(updateData)
            .addOnSuccessListener {
                if (isAdded) Toast.makeText(requireContext(), "Progress updated!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                if (isAdded) Toast.makeText(requireContext(), "Failed to update progress", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}