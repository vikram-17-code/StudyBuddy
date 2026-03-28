package com.example.studybuddy.ui.profile

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.studybuddy.databinding.FragmentEditProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class EditProfileFragment : Fragment() {

    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var selectedImageUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedImageUri = result.data?.data
            binding.editProfileImageView.setImageURI(selectedImageUri)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEditProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        loadCurrentData()

        binding.changePhotoButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            pickImageLauncher.launch(intent)
        }

        binding.saveProfileButton.setOnClickListener {
            saveProfileChanges()
        }

        binding.cancelButton.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun loadCurrentData() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                if (_binding != null && doc.exists()) {
                    binding.editNameEditText.setText(doc.getString("name"))
                    binding.editDescriptionEditText.setText(doc.getString("description"))
                }
            }
            .addOnFailureListener { e ->
                Log.e("EditProfile", "Error loading user data", e)
            }
    }

    private fun saveProfileChanges() {
        val userId = auth.currentUser?.uid ?: run {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }
        
        val newName = binding.editNameEditText.text.toString().trim()
        val newDescription = binding.editDescriptionEditText.text.toString().trim()

        if (newName.isEmpty()) {
            binding.editNameEditText.error = "Name cannot be empty"
            return
        }

        // Show "Saving..." state instead of a spinner
        binding.saveProfileButton.isEnabled = false
        binding.saveProfileButton.text = "Saving..."
        binding.saveProgressBar.visibility = View.GONE

        val updates = hashMapOf<String, Any>(
            "name" to newName,
            "description" to newDescription
        )
        
        db.collection("users").document(userId)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener {
                if (isAdded && _binding != null) {
                    // Change text to "Saved Changes" on success
                    binding.saveProfileButton.text = "Saved Changes"
                    
                    // Small delay so user can see the "Saved Changes" message before going back
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (isAdded && _binding != null) {
                            findNavController().navigateUp()
                        }
                    }, 1000)
                }
            }
            .addOnFailureListener { e ->
                if (isAdded && _binding != null) {
                    // Reset UI state on failure
                    binding.saveProfileButton.isEnabled = true
                    binding.saveProfileButton.text = "Save Changes"
                    
                    Log.e("EditProfile", "Error updating profile", e)
                    Toast.makeText(requireContext(), "Failed to update: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}