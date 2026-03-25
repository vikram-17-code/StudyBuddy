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

    // Sample topics (In a real app, these would be in Firestore "topics" collection)
    private val availableTopics = listOf(
        TopicDetail("daa_t1", "daa_plan", "Asymptotic Analysis", 2, "link_to_daa_1", 1),
        TopicDetail("daa_t2", "daa_plan", "Divide and Conquer", 3, "link_to_daa_2", 2),
        TopicDetail("java_t1", "java_plan", "OOP Concepts", 1, "link_to_java_1", 1),
        TopicDetail("java_t2", "java_plan", "Collections", 2, "link_to_java_2", 2)
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
        taskAdapter = TaskAdapter { userCourse, topic ->
            handleTaskCompletion(userCourse, topic)
        }
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
                if (e != null) {
                    Log.w("HomeFragment", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val taskList = mutableListOf<Pair<UserCourse, TopicDetail>>()
                    for (doc in snapshot) {
                        val userCourse = doc.toObject(UserCourse::class.java)
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
        
        // Log Study History
        val history = StudyHistory(
            historyId = UUID.randomUUID().toString(),
            userId = userId,
            topicId = topic.topicId,
            studyDate = Timestamp.now()
        )
        db.collection("study_history").document(history.historyId).set(history)

        if (userCourse.currentDayNumber < topic.requiredDays) {
            db.collection("user_courses").document(userCourse.userCourseId)
                .update("currentDayNumber", userCourse.currentDayNumber + 1, "lastStudyDate", Timestamp.now())
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Day ${userCourse.currentDayNumber} completed!", Toast.LENGTH_SHORT).show()
                }
        } else {
            val nextTopic = availableTopics.find { 
                it.planId == userCourse.planId && it.topicOrder == topic.topicOrder + 1 
            }
            
            if (nextTopic != null) {
                db.collection("user_courses").document(userCourse.userCourseId)
                    .update(
                        "currentTopicId", nextTopic.topicId,
                        "currentDayNumber", 1,
                        "lastStudyDate", Timestamp.now()
                    ).addOnSuccessListener {
                        Toast.makeText(requireContext(), "Topic completed! Next: ${nextTopic.topicName}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                db.collection("user_courses").document(userCourse.userCourseId)
                    .update("lastStudyDate", Timestamp.now())
                Toast.makeText(requireContext(), "Course Completed!", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}