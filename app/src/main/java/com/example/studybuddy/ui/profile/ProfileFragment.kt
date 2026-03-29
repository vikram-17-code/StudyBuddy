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

        binding.editProfileButton.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_editProfileFragment)
        }

        binding.settingsButton.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_settingsFragment)
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
                val b64 = doc.getString("profilePictureBase64")
                if (!b64.isNullOrEmpty()) {
                    try {
                        val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                        val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        binding.profileImageView.setImageBitmap(bmp)
                    } catch (e: Exception) {
                        android.util.Log.e("ProfileFragment", "Error decoding image", e)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}