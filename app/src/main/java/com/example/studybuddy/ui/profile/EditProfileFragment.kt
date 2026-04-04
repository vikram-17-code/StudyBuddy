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
import android.widget.ArrayAdapter
import android.app.DatePickerDialog
import java.util.Calendar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.studybuddy.R
import com.example.studybuddy.databinding.FragmentEditProfileBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

        val genderOptions = arrayOf("Male", "Female", "Other", "Prefer not to say")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, genderOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.genderSpinner.adapter = adapter

        binding.editDobEditText.setOnClickListener {
            val c = Calendar.getInstance()
            val year = c.get(Calendar.YEAR)
            val month = c.get(Calendar.MONTH)
            val day = c.get(Calendar.DAY_OF_MONTH)
            DatePickerDialog(requireContext(), { _, y, m, d ->
                val dob = String.format("%02d/%02d/%04d", d, m + 1, y)
                binding.editDobEditText.setText(dob)
            }, year, month, day).show()
        }

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
                    
                    val dob = doc.getString("dob")
                    if (!dob.isNullOrEmpty()) {
                        binding.editDobEditText.setText(dob)
                    }

                    val gender = doc.getString("gender")
                    if (!gender.isNullOrEmpty()) {
                        val genderOptions = arrayOf("Male", "Female", "Other", "Prefer not to say")
                        val idx = genderOptions.indexOf(gender)
                        if (idx >= 0) binding.genderSpinner.setSelection(idx)
                    }

                    val b64 = doc.getString("profilePictureBase64")
                    if (!b64.isNullOrEmpty()) {
                        try {
                            val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            binding.editProfileImageView.setImageBitmap(bmp)
                        } catch (e: Exception) {
                            Log.e("EditProfile", "Error decoding image", e)
                        }
                    }
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

        binding.saveProfileButton.isEnabled = false
        binding.saveProfileButton.text = "Saving..."

        val updates = hashMapOf<String, Any>(
            "name" to newName,
            "description" to newDescription,
            "dob" to binding.editDobEditText.text.toString().trim(),
            "gender" to binding.genderSpinner.selectedItem.toString()
        )
        
        if (selectedImageUri != null) {
            Thread {
                try {
                    val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(requireContext().contentResolver, selectedImageUri!!))
                    } else {
                        @Suppress("DEPRECATION")
                        android.provider.MediaStore.Images.Media.getBitmap(requireContext().contentResolver, selectedImageUri!!)
                    }
                    val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, 200, 200, true)
                    val baos = java.io.ByteArrayOutputStream()
                    scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, baos)
                    val b64 = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
                    updates["profilePictureBase64"] = b64
                } catch (e: Exception) {
                    Log.e("EditProfile", "Error encoding image", e)
                }
                Handler(Looper.getMainLooper()).post {
                    pushUpdatesToFirestore(userId, updates)
                }
            }.start()
        } else {
            pushUpdatesToFirestore(userId, updates)
        }
    }

    private fun pushUpdatesToFirestore(userId: String, updates: HashMap<String, Any>) {
        // Fire and forget - Firestore handles offline sync
        db.collection("users").document(userId)
            .set(updates, SetOptions.merge())

        // Optimistic redirect
        if (_binding != null) {
            Toast.makeText(requireContext(), "Profile Updated!", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.profileFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}