package com.example.studybuddy.ui.tasks

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.studybuddy.R
import com.example.studybuddy.databinding.FragmentAddTaskBinding
import com.example.studybuddy.model.CoursePlan
import com.example.studybuddy.model.TopicDetail
import com.example.studybuddy.model.UserCourse
import com.example.studybuddy.notification.NotificationReceiver
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class AddTaskFragment : Fragment() {

    private var _binding: FragmentAddTaskBinding? = null
    private val binding get() = _binding!!
    private lateinit var topicAdapter: TopicListAdapter
    private var userTimeSlots: List<com.example.studybuddy.model.TimeSlot> = emptyList()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val availableCoursePlans = com.example.studybuddy.data.CourseData.availableCoursePlans
    private val availableTopics = com.example.studybuddy.data.CourseData.availableTopics

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        checkUserEnrollment()

        binding.addTimeSlotButton.setOnClickListener {
            showAddTimeSlotDialog()
        }
        
        loadTimeSlots()

        binding.saveTaskButton.setOnClickListener {
            confirmCoursePlan()
        }
    }

    private fun setupRecyclerView() {
        topicAdapter = TopicListAdapter()
        binding.topicsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = topicAdapter
        }
    }

    private fun checkUserEnrollment() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("user_courses")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { documents ->
                if (_binding == null) return@addOnSuccessListener
                val enrolledPlanIds = documents.map { it.toObject(UserCourse::class.java).planId }.toSet()
                setupCourseSpinner(enrolledPlanIds)
            }
    }

    private fun setupCourseSpinner(enrolledPlanIds: Set<String>) {
        val filteredPlans = availableCoursePlans.filter { it.planId !in enrolledPlanIds }
        if (filteredPlans.isEmpty()) {
            Toast.makeText(context, "Already joined all courses", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }

        val names = filteredPlans.map { it.courseName }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.courseSpinner.adapter = adapter

        binding.courseSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedPlanId = filteredPlans[position].planId
                topicAdapter.submitList(availableTopics.filter { it.planId == selectedPlanId })
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun confirmCoursePlan() {
        val userId = auth.currentUser?.uid ?: return
        val selectedItem = binding.courseSpinner.selectedItem?.toString() ?: return
        val selectedPlan = availableCoursePlans.find { it.courseName == selectedItem } ?: return
        
        val selectedSlotPos = binding.timeSlotSpinner.selectedItemPosition
        if (userTimeSlots.isEmpty() || selectedSlotPos == AdapterView.INVALID_POSITION) {
            Toast.makeText(context, "Please select or add a time slot", Toast.LENGTH_SHORT).show()
            return
        }
        val selectedSlot = userTimeSlots[selectedSlotPos]

        binding.saveTaskButton.isEnabled = false

        db.collection("user_courses")
            .whereEqualTo("userId", userId)
            .whereEqualTo("planId", selectedPlan.planId)
            .get()
            .addOnSuccessListener { documents ->
                if (_binding == null || !isAdded) return@addOnSuccessListener
                
                if (!documents.isEmpty) {
                    Toast.makeText(context, "Already enrolled in this course!", Toast.LENGTH_SHORT).show()
                    binding.saveTaskButton.isEnabled = true
                    return@addOnSuccessListener
                }

                val preferredSlot = selectedSlot.slotId
                val firstTopic = availableTopics.find { it.planId == selectedPlan.planId && it.topicOrder == 1 }
                
                val userCourse = UserCourse(
                    userCourseId = UUID.randomUUID().toString(),
                    userId = userId,
                    planId = selectedPlan.planId,
                    currentTopicId = firstTopic?.topicId ?: "",
                    currentDayNumber = 1,
                    preferredSlot = preferredSlot
                )

                db.collection("user_courses")
                    .document(userCourse.userCourseId)
                    .set(userCourse)
                    .addOnSuccessListener {
                        if (_binding != null) {
                            Toast.makeText(requireContext(), "Course Joined!", Toast.LENGTH_SHORT).show()
                            findNavController().navigateUp()
                        }
                    }
                    .addOnFailureListener {
                        if (_binding != null) {
                            binding.saveTaskButton.isEnabled = true
                            Toast.makeText(requireContext(), "Failed to join course", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
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
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
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