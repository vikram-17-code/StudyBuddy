package com.example.studybuddy.ui.tasks

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.studybuddy.R
import com.example.studybuddy.databinding.FragmentAddCustomCourseBinding
import com.example.studybuddy.databinding.ItemSubtopicInputBinding
import com.example.studybuddy.model.CoursePlan
import com.example.studybuddy.model.TopicDetail
import com.example.studybuddy.model.UserCourse
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class AddCustomCourseFragment : Fragment() {

    private var _binding: FragmentAddCustomCourseBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var userTimeSlots: List<com.example.studybuddy.model.TimeSlot> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddCustomCourseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        addSubtopicRow() // Add one initial subtopic row

        binding.addSubtopicButton.setOnClickListener {
            addSubtopicRow()
        }

        binding.addTimeSlotButton.setOnClickListener {
            showAddTimeSlotDialog()
        }

        binding.createCourseButton.setOnClickListener {
            validateAndSaveCourse()
        }

        loadTimeSlots()
    }

    private fun addSubtopicRow() {
        val rowBinding = ItemSubtopicInputBinding.inflate(layoutInflater, binding.subtopicsContainer, false)
        
        rowBinding.removeSubtopicButton.setOnClickListener {
            if (binding.subtopicsContainer.childCount > 1) {
                binding.subtopicsContainer.removeView(rowBinding.root)
            } else {
                Toast.makeText(context, "At least one subtopic is required", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.subtopicsContainer.addView(rowBinding.root)
    }

    private fun loadTimeSlots() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("time_slots")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, _ ->
                if (_binding == null || !isAdded) return@addSnapshotListener
                val slots = mutableListOf<com.example.studybuddy.model.TimeSlot>()
                snapshot?.forEach { doc ->
                    doc.toObject(com.example.studybuddy.model.TimeSlot::class.java).let { slots.add(it) }
                }
                userTimeSlots = slots
                val names = userTimeSlots.map { "${it.name} (${it.timeString})" }
                if (names.isNotEmpty()) {
                    val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    binding.timeSlotSpinner.adapter = adapter
                } else {
                    binding.timeSlotSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, listOf("No slots available"))
                }
            }
    }

    private fun validateAndSaveCourse() {
        val userId = auth.currentUser?.uid ?: return
        val courseName = binding.courseNameInput.text.toString().trim()
        val courseWebsite = binding.courseWebsiteInput.text.toString().trim()
        
        if (courseName.isEmpty()) {
            binding.courseNameInput.error = "Course name required"
            return
        }

        val subtopics = mutableListOf<TopicDetail>()
        val planId = UUID.randomUUID().toString()

        for (i in 0 until binding.subtopicsContainer.childCount) {
            val view = binding.subtopicsContainer.getChildAt(i)
            val nameInput = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.subtopicNameInput)
            val daysInput = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.requiredDaysInput)
            val materialInput = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.materialLinkInput)

            val name = nameInput.text.toString().trim()
            val days = daysInput.text.toString().toIntOrNull() ?: 0
            val material = materialInput.text.toString().trim()

            if (name.isEmpty()) {
                nameInput.error = "Subtopic name required"
                return
            }
            if (days <= 0) {
                daysInput.error = "Must be > 0"
                return
            }

            subtopics.add(TopicDetail(
                topicId = UUID.randomUUID().toString(),
                planId = planId,
                topicName = name,
                requiredDays = days,
                materialLink = material,
                topicOrder = i + 1
            ))
        }

        if (subtopics.isEmpty()) {
            Toast.makeText(context, "Add at least one subtopic", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedSlotPos = binding.timeSlotSpinner.selectedItemPosition
        if (userTimeSlots.isEmpty() || selectedSlotPos < 0) {
            Toast.makeText(context, "Please select or add a time slot", Toast.LENGTH_SHORT).show()
            return
        }
        val preferredSlot = userTimeSlots[selectedSlotPos].slotId

        // OPTIMISTIC NAVIGATION
        binding.createCourseButton.isEnabled = false
        binding.createCourseButton.text = "Redirecting..."
        
        // Save Course Plan in Background
        val coursePlan = CoursePlan(planId, courseName, courseWebsite, userId)
        val batch = db.batch()
        
        val planRef = db.collection("course_plans").document(planId)
        batch.set(planRef, coursePlan)

        subtopics.forEach { topic ->
            val topicRef = db.collection("topics").document(topic.topicId)
            batch.set(topicRef, topic)
        }

        val userCourseId = UUID.randomUUID().toString()
        val userCourse = UserCourse(
            userCourseId = userCourseId,
            userId = userId,
            planId = planId,
            currentTopicId = subtopics[0].topicId,
            currentDayNumber = 1,
            preferredSlot = preferredSlot
        )
        val enrollmentRef = db.collection("user_courses").document(userCourseId)
        batch.set(enrollmentRef, userCourse)

        // Fire the batch and forget (Firestore handles sync)
        batch.commit()

        // Instant redirect
        Toast.makeText(requireContext(), "Custom Course '$courseName' Created!", Toast.LENGTH_SHORT).show()
        findNavController().navigate(R.id.homeFragment)
    }

    private fun showAddTimeSlotDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_time_slot, null)
        val nameInput = dialogView.findViewById<android.widget.EditText>(R.id.slotNameInput)
        val cbSun = dialogView.findViewById<android.widget.CheckBox>(R.id.cbSun)
        val cbMon = dialogView.findViewById<android.widget.CheckBox>(R.id.cbMon)
        val cbTue = dialogView.findViewById<android.widget.CheckBox>(R.id.cbTue)
        val cbWed = dialogView.findViewById<android.widget.CheckBox>(R.id.cbWed)
        val cbThu = dialogView.findViewById<android.widget.CheckBox>(R.id.cbThu)
        val cbFri = dialogView.findViewById<android.widget.CheckBox>(R.id.cbFri)
        val cbSat = dialogView.findViewById<android.widget.CheckBox>(R.id.cbSat)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Time Slot")
            .setView(dialogView)
            .setPositiveButton("Next") { _, _ ->
                val slotName = nameInput.text.toString().trim()
                val selectedDays = mutableListOf<Int>()
                if (cbSun.isChecked) selectedDays.add(Calendar.SUNDAY)
                if (cbMon.isChecked) selectedDays.add(Calendar.MONDAY)
                if (cbTue.isChecked) selectedDays.add(Calendar.TUESDAY)
                if (cbWed.isChecked) selectedDays.add(Calendar.WEDNESDAY)
                if (cbThu.isChecked) selectedDays.add(Calendar.THURSDAY)
                if (cbFri.isChecked) selectedDays.add(Calendar.FRIDAY)
                if (cbSat.isChecked) selectedDays.add(Calendar.SATURDAY)

                if (slotName.isNotEmpty() && selectedDays.isNotEmpty()) {
                    pickTimeForSlot(slotName, selectedDays)
                } else {
                    Toast.makeText(requireContext(), "Name and at least one day required", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickTimeForSlot(slotName: String, selectedDays: List<Int>) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(requireContext(), { _, hourOfDay, minute ->
            saveTimeSlot(slotName, hourOfDay, minute, selectedDays)
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
    }

    private fun saveTimeSlot(name: String, hour: Int, minute: Int, selectedDays: List<Int>) {
        val userId = auth.currentUser?.uid ?: return
        val slotId = UUID.randomUUID().toString()
        val timeString = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
        val timeSlot = com.example.studybuddy.model.TimeSlot(
            slotId = slotId,
            userId = userId,
            name = name,
            timeString = timeString,
            hour = hour,
            minute = minute,
            selectedDays = selectedDays
        )
        db.collection("time_slots").document(slotId).set(timeSlot)
            .addOnSuccessListener {
                if (isAdded) {
                    Toast.makeText(context, "Time Slot Added", Toast.LENGTH_SHORT).show()
                    com.example.studybuddy.notification.NotificationHelper.scheduleTimeSlotAlarm(requireContext(), timeSlot)
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}