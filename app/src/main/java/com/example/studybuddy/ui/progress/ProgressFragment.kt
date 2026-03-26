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
        _binding = FragmentProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        loadProgress()
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
                    var totalCompletion = 0

                    userCourses.forEach { userCourse ->
                        val topics = availableTopics.filter { it.planId == userCourse.planId }
                        val progress = calculateCourseProgress(userCourse, topics)
                        displayList.add(Triple(userCourse, topics, progress))
                        totalCompletion += progress
                    }

                    adapter.submitList(displayList)
                    
                    val overallProgress = if (userCourses.isNotEmpty()) totalCompletion / userCourses.size else 0
                    binding.overallProgressBar.progress = overallProgress
                    binding.progressPercentageTextView.text = "$overallProgress%"
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}