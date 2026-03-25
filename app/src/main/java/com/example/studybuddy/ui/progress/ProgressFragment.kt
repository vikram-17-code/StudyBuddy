package com.example.studybuddy.ui.progress

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.studybuddy.databinding.FragmentProgressBinding
import com.example.studybuddy.model.UserCourse
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProgressFragment : Fragment() {
    private var _binding: FragmentProgressBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadProgress()
    }

    private fun loadProgress() {
        val userId = auth.currentUser?.uid ?: return
        
        db.collection("user_courses")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val courses = snapshot.toObjects(UserCourse::class.java)
                    calculateOverallProgress(courses)
                }
            }
    }

    private fun calculateOverallProgress(courses: List<UserCourse>) {
        if (courses.isEmpty()) {
            binding.progressPercentageTextView.text = "No courses joined"
            binding.overallProgressBar.progress = 0
            return
        }

        // Simplistic progress calculation for demo
        // In real app, you'd compare currentTopicOrder/TotalTopics
        var totalProgress = 0
        courses.forEach { _ ->
            totalProgress += 25 // Arbitrary progress per course for now
        }
        
        val displayProgress = totalProgress.coerceAtMost(100)
        binding.overallProgressBar.progress = displayProgress
        binding.progressPercentageTextView.text = "$displayProgress% Overall Completion"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}