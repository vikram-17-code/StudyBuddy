package com.example.studybuddy.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.studybuddy.R
import com.example.studybuddy.databinding.FragmentProfileBinding
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

        binding.logoutButton.setOnClickListener {
            auth.signOut()
            // Redirect to Login page and clear the navigation backstack
            findNavController().navigate(R.id.loginFragment)
        }
    }

    private fun loadUserData() {
        val user = auth.currentUser ?: return
        binding.userEmailTextView.text = user.email
        
        db.collection("users").document(user.uid).get().addOnSuccessListener { doc ->
            if (_binding != null) {
                binding.userNameTextView.text = doc.getString("name") ?: "Student"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}