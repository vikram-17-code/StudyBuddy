package com.example.studybuddy.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.studybuddy.R
import com.example.studybuddy.databinding.FragmentProfileBinding
import com.example.studybuddy.model.UserCourse
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadUserData()
        loadUserBadges()

        binding.editProfileButton.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_editProfileFragment)
        }

        binding.logoutButton.setOnClickListener {
            auth.signOut()
            // Redirect to Login page and clear the navigation backstack
            findNavController().navigate(R.id.loginFragment)
        }
    }

    private fun loadUserData() {
        val user = auth.currentUser ?: return
        binding.userEmailTextView.text = user.email
        
        db.collection("users").document(user.uid).addSnapshotListener { doc, _ ->
            if (_binding != null && doc != null && doc.exists()) {
                binding.userNameTextView.text = doc.getString("name") ?: "Student"
                binding.userDescriptionTextView.text = doc.getString("description") ?: "No description set"
            }
        }
    }

    private fun loadUserBadges() {
        val userId = auth.currentUser?.uid ?: return
        
        db.collection("user_courses")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, _ ->
                if (_binding == null || snapshot == null) return@addSnapshotListener
                
                val completedPlans = snapshot.documents.mapNotNull { doc ->
                    val course = doc.toObject(UserCourse::class.java)
                    if (course?.currentTopicId == "COMPLETED") course.planId else null
                }.toSet()

                updateBadgesUI(completedPlans)
            }
    }

    private fun updateBadgesUI(completedPlans: Set<String>) {
        if (_binding == null) return

        // Update DAA Badge
        if ("daa_plan" in completedPlans) {
            binding.badgeDaa.setImageResource(R.drawable.ic_badge_daa)
        } else {
            binding.badgeDaa.setImageResource(R.drawable.ic_badge_locked)
        }

        // Update JAVA Badge
        if ("java_plan" in completedPlans) {
            binding.badgeJava.setImageResource(R.drawable.ic_badge_java)
        } else {
            binding.badgeJava.setImageResource(R.drawable.ic_badge_locked)
        }

        // Update DSA Badge
        if ("dsa_plan" in completedPlans) {
            binding.badgeDsa.setImageResource(R.drawable.ic_badge_dsa)
        } else {
            binding.badgeDsa.setImageResource(R.drawable.ic_badge_locked)
        }

        // Update WEB Badge
        if ("web_plan" in completedPlans) {
            binding.badgeWeb.setImageResource(R.drawable.ic_badge_web)
        } else {
            binding.badgeWeb.setImageResource(R.drawable.ic_badge_locked)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}